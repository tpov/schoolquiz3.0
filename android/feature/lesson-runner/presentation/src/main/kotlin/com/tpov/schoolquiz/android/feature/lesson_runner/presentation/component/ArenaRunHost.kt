package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component

import com.arkivanov.essenty.instancekeeper.InstanceKeeper
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ArenaRunnerRequest
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui.buildHintDraft
import com.tpov.schoolquiz.shared.core.sync.SyncScheduler
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRunnerPhase
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketHeader
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketStatus
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptIdentity
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ServerAttemptSettlement
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ServerAttemptSettlementState
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaEntryRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaFrozenRatingRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaRunDecision
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaRunRead
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaRunRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionObserver
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionProvider
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionStamp
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaWalletRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ServerAttemptSettlementRepository
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlin.coroutines.CoroutineContext

/** Retained owner, scope and presentation intent. Never retain an Activity or ComponentContext here. */
internal class ArenaRunHost(

    request: ArenaRunnerRequest,
    private val services: ArenaRunServices,
    private val clock: Clock,
    mainContext: CoroutineContext,
) : InstanceKeeper.Instance {
    private val scope = CoroutineScope(mainContext + SupervisorJob())
    private val stamp =
        request.fresh?.session ?: request.initiatingSession
            ?: services.sessions.current()?.takeIf { it.ownerUid == request.ownerUid }
    private val ticketId = request.ticketId
    private val coordinator =
        stamp?.let {
            ArenaRunCoordinator(it, ticketId, services.runs, services.sessions, scope, clock)
        }
    private val visibility = ArenaRunVisibility()
    private val budget = ArenaRunHintBudget(stamp, services.wallet, services.sessions, scope, clock, ::publish)
    private val rating =
        ArenaRunRating(services.result.ratings, services.sessions, services.result.sync, mainContext)
    private val mutableView = MutableStateFlow<ArenaRunnerView>(ArenaRunnerView.Attention(ArenaRunnerStatus.LOADING))
    val view = mutableView.asStateFlow()
    private var showExit = false
    private var cancelDispatching = false
    private var destroyed = false
    private var receiptKey: ArenaReceiptKey? = null
    private var receiptFrame: ArenaRunnerView.Result? = null

    /** Поднятый флаг требует решения игрока по платным условиям (см. sync()). */
    val needsTermsConsent = MutableStateFlow(false)

    init {
        val controller = coordinator
        if (controller == null) {
            mutableView.value = ArenaRunnerView.Attention(ArenaRunnerStatus.ACCOUNT_CHANGED)
        } else {
            scope.launch {
                services.sessionEvents.observeSessions().collect {
                    rating.onSessionChanged()
                    if (!controller.checkSession()) {
                        budget.clear()
                        clearReceipt()
                        publish()
                    }
                }
            }
            scope.launch { controller.state.collect { reconcile() } }
            scope.launch { rating.state.collect { publish() } }
            observeReceipts(controller)
            val intent = request.fresh?.let { ArenaRunStart.Fresh(it.mode) } ?: ArenaRunStart.ResumeOnly
            controller.start(intent)
        }
    }

    fun action(
        token: ArenaRunToken,
        action: ArenaRunAction,
    ) {
        val controller = coordinator
        val interactive = !destroyed && visibility.foreground && !showExit
        if (controller != null && interactive && token == controller.token()) {
            val permitted = visibleAction(token, action) ?: return
            if (permitted == ArenaRunAction.AdvanceToPause || permitted == ArenaRunAction.TimeoutToPause) {
                visibility.advancing(token)
            }
            controller.dispatch(token, permitted)
        }
    }

    private fun visibleAction(
        token: ArenaRunToken,
        action: ArenaRunAction,
    ): ArenaRunAction? =
        when (action) {
            ArenaRunAction.Continue ->
                when (token.phase) {
                    ArenaRunnerPhase.FEEDBACK -> ArenaRunAction.AdvanceToPause
                    ArenaRunnerPhase.PAUSED -> action
                    else -> null
                }
            ArenaRunAction.Timeout ->
                action.takeIf { token.phase == ArenaRunnerPhase.ANSWERING }
                    ?.let { ArenaRunAction.TimeoutToPause }
            is ArenaRunAction.Draft, is ArenaRunAction.Answer ->
                action.takeIf { token.phase == ArenaRunnerPhase.ANSWERING }
            is ArenaRunAction.Hint -> action.takeIf { validHint(it) }
            // Background, prepared reveal and other internal transitions are not public frame callbacks.
            else -> null
        }

    private fun validHint(action: ArenaRunAction.Hint): Boolean {
        val current = coordinator?.state?.value?.observed as? ArenaRunRead.Continuable
        val question = (view.value as? ArenaRunnerView.Question)?.question
        if (current == null || question == null) return false
        val allowsHint =
            current.checkpoint.phase == ArenaRunnerPhase.ANSWERING && question.revealCorrect && !question.isHard
        val hasBudget = (budget.lives(current) ?: 0) > 0
        return allowsHint && hasBudget && action.answer == buildHintDraft(question.questionUiState)
    }

    fun foreground() {
        visibility.foreground()
        publish()
    }

    fun background(changingConfigurations: Boolean) {
        if (!destroyed && !changingConfigurations) {
            visibility.background(false, clock.now().toEpochMilliseconds())
            showExit = false
            reconcile()
        }
    }

    fun reload() {
        coordinator?.reload()
    }

    fun refreshWallet() {
        if (coordinator?.checkSession() == true) budget.refresh()
    }

    fun requestExit() {
        if (coordinator?.token() != null) {
            showExit = true
            publish()
        }
    }

    fun dismissExit() {
        showExit = false
        publish()
    }

    /** Confirmation ID comes from the retained confirmation, not from each network retry. */
    fun confirmExit(requestId: String) {
        if (showExit) {
            showExit = false
            coordinator?.cancel(services.entries, requestId)
        }
    }

    fun sync() {
        val controller = coordinator ?: return
        val state = controller.state.value
        val pending = state.observed as? ArenaRunRead.DecisionPending
        if (!destroyed && controller.checkSession() && !state.busy) {
            when {
                pending?.decision == ArenaRunDecision.CANCEL -> dispatchCancel()
                // Диалог поднимается только когда у этого прогона есть незакрытая работа
                // (наблюдаемое решение SETTLE/CANCEL — ретро L5): голый вердикт гейта без
                // запаркованной мутации диалога не заслуживает — отказ terms-consent запарковал
                // SETTLE в CONFLICT, без решения игрока повтор бессмыслен (CAP-16, AD-5).
                pending != null && services.result.consent?.let { !it.satisfied() } == true -> {
                    needsTermsConsent.value = true
                }
                state.canRetryReceiptSync(
                    receiptFrame?.settlement ?: pendingSettlement(),
                ) -> services.result.sync.enqueueManualSync()
            }
        }
    }

    /** Решение игрока за условиями: согласие возвращает запаркованный SETTLE в отправку. */
    fun acceptTerms() {
        Log.d(TAG, "terms consent: accept requested")
        scope.launch {
            try {
                services.result.consent?.accept()
                Log.d(TAG, "terms consent: accepted, requeue done")
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                Log.e(TAG, "terms consent: accept failed", error)
                return@launch
            }
            needsTermsConsent.value = false
            sync()
        }
    }

    /** «Позже»/dismiss: флаг сбрасывается без ухода с экрана; явный sync может поднять диалог снова. */
    fun dismissTermsConsent() {
        needsTermsConsent.value = false
    }

    private fun dispatchCancel() {
        val original = stamp ?: return
        if (cancelDispatching) return
        cancelDispatching = true
        scope.launch {
            try {
                services.entries.dispatchCancel(original, ticketId)
                coordinator?.reload()
            } finally {
                cancelDispatching = false
            }
        }
    }

    private fun reconcile() {
        val controller = coordinator ?: return
        val current = controller.state.value
        reconcileReceipt(current)
        if (current.notice != null) visibility.writeFailed()
        if (!current.busy && current.notice == null && controller.checkSession()) {
            val token = controller.token()
            val startedAt = (current.observed as? ArenaRunRead.Continuable)?.checkpoint?.questionStartedAtMs ?: 0L
            val transition = token?.let { visibility.afterSaved(it, startedAt) }
            when {
                token != null && transition != null -> {
                    controller.dispatch(token, transition)
                }
                current.observed is ArenaRunRead.Completing -> {
                    controller.freeze()
                }
                else -> publish()
            }
        } else {
            publish()
        }
    }

    private fun publish() {
        if (destroyed) return
        val controller = coordinator
        val original = stamp
        if (controller != null && original != null) {
            controller.checkSession()
            val state = controller.state.value
            reconcileReceipt(state)
            val current = state.observed as? ArenaRunRead.Continuable
            val result = receiptFrameFor(state, original)
            rating.bind(result?.toVerifiedResult())
            mutableView.value =
                result?.copy(rating = rating.state.value) ?: arenaRunView(
                    state,
                    original,
                    visibility.foreground,
                    current?.let(budget::lives),
                    showExit,
                )
            val question = view.value as? ArenaRunnerView.Question
            if (question != null && !question.busy) visibility.presented(question.token)
        }
    }

    private fun reconcileReceipt(state: ArenaRunCoordinatorState) {
        val original = stamp
        val candidate = original?.let { state.takeReceiptCandidate(it, ticketId) }
        when {
            destroyed -> clearReceipt()
            original == null -> clearReceipt()
            candidate == null -> clearReceipt()
            services.sessions.current() != original -> clearReceipt()
            else -> writePendingReceipt(state, original, candidate)
        }
    }

    private fun writePendingReceipt(
        state: ArenaRunCoordinatorState,
        original: ArenaSessionStamp,
        candidate: ArenaReceiptCandidate,
    ) {
        val key = ArenaReceiptKey(original, ticketId, candidate.attempt)
        if (receiptKey != key) {
            receiptKey = key
            receiptFrame =
                ArenaRunnerView.Result(
                    original,
                    candidate.header,
                    candidate.attempt,
                    pendingSettlement(),
                    state.canRetryReceiptSync(pendingSettlement()),
                )
        }
    }

    private fun observeReceipts(controller: ArenaRunCoordinator) {
        val original = requireNotNull(stamp)
        scope.launch {
            controller.state
                .map { state ->
                    state.takeReceiptCandidate(original, ticketId)?.let { candidate ->
                        ArenaReceiptKey(original, ticketId, candidate.attempt)
                    }
                }
                .distinctUntilChanged()
                .flatMapLatest { key ->
                    key?.let { frozen ->
                        services.result.settlements.observe(frozen.attempt).map { ArenaReceiptEmission(frozen, it) }
                    } ?: flowOf(null)
                }
                .collect { emission ->
                    if (emission == null) {
                        clearReceipt()
                    } else if (!destroyed && services.sessions.current() == original) {
                        val current = controller.state.value.takeReceiptCandidate(original, ticketId)
                        if (current?.attempt == emission.key.attempt) {
                            receiptKey = emission.key
                            receiptFrame =
                                ArenaRunnerView.Result(
                                    original,
                                    current.header,
                                    emission.key.attempt,
                                    emission.settlement,
                                    controller.state.value.canRetryReceiptSync(emission.settlement),
                                )
                        }
                    }
                    publish()
                }
        }
    }

    private fun clearReceipt() {
        receiptKey = null
        receiptFrame = null
        rating.bind(null)
    }

    /** UI callbacks must still name the exact rendered receipt after a retained-host state change. */
    fun submitRating(
        frame: ArenaRunnerView.Result,
        ratingValue: Int,
    ) {
        verifiedCurrentResult(frame)?.let {
            rating.bind(it)
            rating.submit(ratingValue)
        }
    }

    fun requestRatingSync(frame: ArenaRunnerView.Result) {
        verifiedCurrentResult(frame)?.let {
            rating.bind(it)
            rating.requestSync()
        }
    }

    private fun verifiedCurrentResult(frame: ArenaRunnerView.Result): ArenaVerifiedResult? =
        currentReceiptFrame()
            ?.let { current ->
                frame
                    .takeIf { it == current.copy(rating = frame.rating) }
                    ?.toVerifiedResult()
            }

    private fun currentReceiptFrame(): ArenaRunnerView.Result? =
        coordinator?.state?.value?.let { state ->
            stamp?.let { original -> receiptFrameFor(state, original) }
        }

    private fun receiptFrameFor(
        state: ArenaRunCoordinatorState,
        original: ArenaSessionStamp,
    ): ArenaRunnerView.Result? =
        state.takeReceiptCandidate(original, ticketId)?.let { candidate ->
            val key = ArenaReceiptKey(original, ticketId, candidate.attempt)
            receiptFrame
                ?.takeIf { receiptKey == key && services.sessions.current() == original }
                ?.let { receipt ->
                    receipt.copy(
                        header = candidate.header,
                        canSync = state.canRetryReceiptSync(receipt.settlement),
                    )
                }
        }

    override fun onDestroy() {
        destroyed = true
        rating.onDestroy()
        receiptKey = null
        receiptFrame = null
        scope.cancel()
        budget.clear()
        mutableView.value = ArenaRunnerView.Attention(ArenaRunnerStatus.ACCOUNT_CHANGED)
    }
}

/** The observer key contains the captured epoch so same-UID ABA sessions cannot reuse a receipt. */
private data class ArenaReceiptKey(
    val stamp: ArenaSessionStamp,
    val ticketId: String,
    val attempt: AttemptIdentity,
)

private data class ArenaReceiptCandidate(
    val header: ArenaTicketHeader,
    val attempt: AttemptIdentity,
)

private data class ArenaReceiptEmission(
    val key: ArenaReceiptKey,
    val settlement: ServerAttemptSettlement,
)

private fun ArenaRunCoordinatorState.takeReceiptCandidate(
    stamp: ArenaSessionStamp,
    ticketId: String,
): ArenaReceiptCandidate? =
    if (notice == null) {
        receiptHeader()
            ?.takeIf { it.ownerUid == stamp.ownerUid && it.ticketId == ticketId }
            ?.let { header ->
                runCatching {
                    AttemptIdentity(
                        id = AttemptId(header.attemptId),
                        userId = stamp.ownerUid,
                        lessonId = LessonId(header.lessonId),
                        lessonVersion = header.lessonVersion,
                        mode = header.difficulty,
                    )
                }.getOrNull()?.let { ArenaReceiptCandidate(header, it) }
            }
    } else {
        null
    }

private fun ArenaRunCoordinatorState.receiptHeader(): ArenaTicketHeader? =
    when (val current = observed) {
        is ArenaRunRead.DecisionPending -> current.header.takeIf { current.decision == ArenaRunDecision.SETTLE }
        is ArenaRunRead.Terminal -> current.header.takeIf { it.status == ArenaTicketStatus.SETTLED }
        else -> null
    }

private fun pendingSettlement() = ServerAttemptSettlement(ServerAttemptSettlementState.PENDING)

/**
 * A manual sync can replay only a durable, still-pending SETTLE command. PENDING is the normal
 * wait; UNSCORABLE and INVALID_RECEIPT may be repaired by a later authoritative receipt. REJECTED,
 * LEGACY_RECORDED and ACCOUNT_UNAVAILABLE are not retried from this retained ticket screen.
 */
private fun ArenaRunCoordinatorState.canRetryReceiptSync(settlement: ServerAttemptSettlement): Boolean =
    observed is ArenaRunRead.DecisionPending &&
        (observed as ArenaRunRead.DecisionPending).decision == ArenaRunDecision.SETTLE &&
        !settlement.isConfirmedCompletion &&
        settlement.state in
        setOf(
            ServerAttemptSettlementState.PENDING,
            ServerAttemptSettlementState.UNSCORABLE,
            ServerAttemptSettlementState.INVALID_RECEIPT,
        )

internal data class ArenaRunServices(
    val runs: ArenaRunRepository,
    val entries: ArenaEntryRepository,
    val sessions: ArenaSessionProvider,
    val sessionEvents: ArenaSessionObserver,
    val wallet: ArenaWalletRepository,
    val result: ArenaRunResultServices,
)

/** Result ports stay together so the main retained-host constructor remains small and legible. */
internal data class ArenaRunResultServices(
    val settlements: ServerAttemptSettlementRepository,
    val ratings: ArenaFrozenRatingRepository,
    val sync: SyncScheduler,
    /**
     * Платное согласие (CAP-16): SETTLE гейтится согласием с условиями, и откaz
     * terms-consent-required парковает строку очереди в CONFLICT. `satisfied` сверяет
     * захваченные условия с таблицей, `accept` фиксирует решение игрока и возвращает ждущие
     * записи в отправку. Null — хост без гейта (тесты, простые сборки).
     */
    val consent: ArenaConsentHandles? = null,
)

/** Ручки согласия хоста; живут лямбдами, чтобы runner не зависел от economy-модуля. */
internal data class ArenaConsentHandles(
    val satisfied: () -> Boolean,
    val accept: suspend () -> Unit,
)

private fun ArenaRunnerView.Result.toVerifiedResult(): ArenaVerifiedResult? =
    takeIf { settlement.isConfirmedCompletion }?.let {
        ArenaVerifiedResult(
            stamp,
            header.ticketId,
            attempt,
            settlement,
        )
    }

private const val TAG = "ArenaConsent"
