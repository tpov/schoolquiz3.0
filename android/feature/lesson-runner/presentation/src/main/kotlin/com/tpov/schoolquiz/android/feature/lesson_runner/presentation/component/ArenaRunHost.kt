package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component

import com.arkivanov.essenty.instancekeeper.InstanceKeeper
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ArenaRunnerRequest
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui.buildHintDraft
import com.tpov.schoolquiz.shared.core.sync.SyncScheduler
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRunnerPhase
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaEntryRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaRunDecision
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaRunRead
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaRunRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionObserver
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionProvider
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaWalletRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
        request.fresh?.session ?: services.sessions.current()?.takeIf { it.ownerUid == request.ownerUid }
    private val ticketId = request.ticketId
    private val coordinator =
        stamp?.let {
            ArenaRunCoordinator(it, ticketId, services.runs, services.sessions, scope, clock)
        }
    private val visibility = ArenaRunVisibility()
    private val budget = ArenaRunHintBudget(stamp, services.wallet, services.sessions, scope, clock, ::publish)
    private val mutableView = MutableStateFlow<ArenaRunnerView>(ArenaRunnerView.Attention(ArenaRunnerStatus.LOADING))
    val view = mutableView.asStateFlow()
    private var showExit = false
    private var cancelDispatching = false
    private var destroyed = false

    init {
        val controller = coordinator
        if (controller == null) {
            mutableView.value = ArenaRunnerView.Attention(ArenaRunnerStatus.ACCOUNT_CHANGED)
        } else {
            scope.launch {
                services.sessionEvents.observeSessions().collect {
                    if (!controller.checkSession()) {
                        budget.clear()
                        publish()
                    }
                }
            }
            scope.launch { controller.state.collect { reconcile() } }
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
        val pending = controller.state.value.observed as? ArenaRunRead.DecisionPending
        if (!destroyed && controller.checkSession() && !controller.state.value.busy) {
            if (pending?.decision == ArenaRunDecision.CANCEL) dispatchCancel() else services.sync.enqueueManualSync()
        }
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
            val current = state.observed as? ArenaRunRead.Continuable
            mutableView.value =
                arenaRunView(
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

    override fun onDestroy() {
        destroyed = true
        scope.cancel()
        budget.clear()
        mutableView.value = ArenaRunnerView.Attention(ArenaRunnerStatus.ACCOUNT_CHANGED)
    }
}

internal data class ArenaRunServices(
    val runs: ArenaRunRepository,
    val entries: ArenaEntryRepository,
    val sessions: ArenaSessionProvider,
    val sessionEvents: ArenaSessionObserver,
    val wallet: ArenaWalletRepository,
    val sync: SyncScheduler,
)
