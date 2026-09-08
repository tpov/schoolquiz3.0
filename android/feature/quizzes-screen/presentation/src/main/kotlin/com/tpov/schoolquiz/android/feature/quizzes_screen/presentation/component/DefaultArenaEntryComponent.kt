package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.instancekeeper.InstanceKeeper
import com.arkivanov.essenty.instancekeeper.getOrCreate
import com.arkivanov.essenty.lifecycle.doOnResume
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaEntriesCursor
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaEntryCommandOutcome
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaEntryResolution
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLessonCatalogBounds
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaPreparationsCursor
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaPrepareRequest
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRecoveryCursor
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaEntryRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionObserver
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionProvider
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionStamp
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaWalletRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext

data class ArenaEntryDependencies(
    val entries: ArenaEntryRepository,
    val wallet: ArenaWalletRepository,
    val sessions: ArenaSessionProvider,
    val sessionEvents: ArenaSessionObserver,
)

/** No financial actions or reads on construction. Rotation retains work; process death restores from journal. */
class DefaultArenaEntryComponent(
    context: ComponentContext,
    dependencies: ArenaEntryDependencies,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    ids: () -> String = { UUID.randomUUID().toString() },
    nowMs: () -> Long = System::currentTimeMillis,
) : ComponentContext by context, ArenaEntryComponent {
    private val holder =
        instanceKeeper.getOrCreate("arena_entry") {
            ArenaEntryStateHolder(dependencies, dispatcher, ids, nowMs)
        }
    override val state = holder.state

    init {
        holder.recheckSession()
        lifecycle.doOnResume { holder.recheckSession() }
    }

    override fun onEvent(event: ArenaEntryEvent) = holder.onEvent(event)
}

/** All events and mutable state are confined to the supplied UI dispatcher. */
internal class ArenaEntryStateHolder(
    private val deps: ArenaEntryDependencies,
    dispatcher: CoroutineDispatcher,
    private val ids: () -> String,
    private val nowMs: () -> Long,
) : InstanceKeeper.Instance {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var session = deps.sessions.current()
    private var active: EntryOperation? = null
    private var destroyed = false
    private var sessionStreamHealthy = true
    private var ticketsCursor: ArenaEntriesCursor? = null
    private var preparationsCursor: ArenaPreparationsCursor? = null
    private var recoveryCursor: ArenaRecoveryCursor? = null
    private val blockedPreparations = mutableSetOf<String>()
    private val mutableState = MutableStateFlow(initial(session))
    val state = mutableState.asStateFlow()

    init {
        scope.launch {
            deps.sessionEvents.observeSessions().catch {
                if (it is CancellationException) throw it
                sessionStreamHealthy = false
                reset(null)
            }.collect { recheckSession() }
        }
    }

    fun recheckSession(): ArenaSessionStamp? {
        val actual = if (destroyed || !sessionStreamHealthy) null else deps.sessions.current()
        if (actual != session) reset(actual)
        val wallet = mutableState.value.wallet
        if (wallet != null && !wallet.isUsableFor(actual, nowMs(), WALLET_MAX_AGE_MS)) {
            mutableState.value = mutableState.value.copy(wallet = null)
        }
        return actual
    }

    fun onEvent(event: ArenaEntryEvent) {
        if (destroyed || recheckSession() != event.session || active != null) return
        when (event) {
            is ArenaEntryEvent.Load -> load(event)
            is ArenaEntryEvent.ShowPrepare -> {
                if (event.lesson != null && event.lesson.priceNolics !in 1..ArenaLessonCatalogBounds.MAX_PRICE) return
                mutableState.value = mutableState.value.copy(dialog = ArenaEntryDialog.Prepare(ids(), event.lesson))
            }
            is ArenaEntryEvent.ShowCancel -> showCancel(event)
            is ArenaEntryEvent.ChangeDifficulty -> changeDifficulty(event)
            is ArenaEntryEvent.Dismiss ->
                if (mutableState.value.dialog?.id == event.dialogId) {
                    mutableState.value = mutableState.value.copy(dialog = null)
                }
            is ArenaEntryEvent.Confirm -> confirm(event)
            is ArenaEntryEvent.RetryPreparation -> retryPreparation(event)
            is ArenaEntryEvent.TicketAction -> ticketAction(event)
        }
    }

    private fun changeDifficulty(event: ArenaEntryEvent.ChangeDifficulty) {
        val dialog = mutableState.value.dialog as? ArenaEntryDialog.Prepare ?: return
        if (dialog.id == event.dialogId) {
            mutableState.value = mutableState.value.copy(dialog = dialog.copy(difficulty = event.difficulty))
        }
    }

    private fun showCancel(event: ArenaEntryEvent.ShowCancel) {
        val ticket = mutableState.value.tickets.find { it.ticketId == event.ticketId } ?: return
        if (ticket.canOfferCancellation()) {
            mutableState.value =
                mutableState.value.copy(
                    dialog = ArenaEntryDialog.Cancel(ids(), ticket),
                )
        }
    }

    private fun confirm(event: ArenaEntryEvent.Confirm) {
        val dialog = mutableState.value.dialog ?: return
        if (dialog.id != event.dialogId) return
        val requestId = ids() // Frozen before the first suspension, never regenerated by retry.
        when (dialog) {
            is ArenaEntryDialog.Prepare -> {
                val request = dialog.request(event.session.ownerUid, requestId)
                start(event.session, invalidateWallet = true, requestId = requestId) { op ->
                    await(op) { deps.entries.recordPrepareConsent(op.session, request) }
                    applyOutcome(await(op) { deps.entries.dispatchPrepare(op.session, requestId) }, requestId)
                    reloadLocal(op)
                }
            }
            is ArenaEntryDialog.Cancel ->
                start(event.session, invalidateWallet = true, requestId = requestId) { op ->
                    await(op) { deps.entries.recordCancelIntent(op.session, dialog.ticket.ticketId, requestId) }
                    applyOutcome(await(op) { deps.entries.dispatchCancel(op.session, dialog.ticket.ticketId) })
                    reloadLocal(op)
                }
        }
    }

    private fun retryPreparation(event: ArenaEntryEvent.RetryPreparation) {
        val row = mutableState.value.preparations.find { it.requestId == event.requestId } ?: return
        if (!row.canRetryPreparation() || row.requestId in blockedPreparations) return
        start(event.session, invalidateWallet = true, requestId = row.requestId) { op ->
            applyOutcome(await(op) { deps.entries.dispatchPrepare(op.session, row.requestId) }, row.requestId)
            reloadLocal(op)
        }
    }

    private fun ticketAction(event: ArenaEntryEvent.TicketAction) {
        val row = mutableState.value.tickets.find { it.ticketId == event.ticketId } ?: return
        if (event.action !in row.availableEntryActions()) return
        start(event.session, invalidateWallet = true) { op ->
            when (event.action) {
                ArenaTicketAction.RECOVER -> await(op) { deps.entries.recoverTicket(op.session, row.ticketId) }
                ArenaTicketAction.RETRY_CANCEL ->
                    applyOutcome(
                        await(op) { deps.entries.dispatchCancel(op.session, row.ticketId) },
                    )
                ArenaTicketAction.RESTORE_SETTLEMENT -> {
                    await(op) { deps.entries.restorePendingSettlement(op.session, row.ticketId) }
                    notice(ArenaEntryNotice.RESTORED)
                }
                ArenaTicketAction.RECONCILE_CANCELLED ->
                    acceptResolution(
                        await(op) { deps.entries.reconcileCancelledSettlement(op.session, row.ticketId) },
                    )
            }
            reloadLocal(op)
        }
    }

    private fun load(event: ArenaEntryEvent.Load) {
        if (event.append && !canAppend(event.page)) return
        start(event.session) { op ->
            when (event.page) {
                ArenaEntryPage.TICKETS -> loadTickets(op, event.append)
                ArenaEntryPage.PREPARATIONS -> loadPreparations(op, event.append)
                ArenaEntryPage.RECOVERY -> {
                    val page =
                        await(
                            op,
                        ) { deps.entries.recoverPage(op.session, cursor = if (event.append) recoveryCursor else null) }
                    recoveryCursor = page.nextCursor
                    mutableState.value = mutableState.value.copy(canRecoverMore = recoveryCursor != null)
                    reloadLocal(op)
                }
                ArenaEntryPage.WALLET -> {
                    mutableState.value = mutableState.value.copy(wallet = null)
                    val wallet = await(op) { deps.wallet.read(op.session) }
                    if (!wallet.isUsableFor(op.session, nowMs(), WALLET_MAX_AGE_MS)) {
                        notice(ArenaEntryNotice.WALLET_UNKNOWN)
                    } else {
                        mutableState.value = mutableState.value.copy(wallet = wallet)
                    }
                }
            }
        }
    }

    private fun canAppend(page: ArenaEntryPage): Boolean =
        when (page) {
            ArenaEntryPage.TICKETS -> ticketsCursor != null
            ArenaEntryPage.PREPARATIONS -> preparationsCursor != null
            ArenaEntryPage.RECOVERY -> recoveryCursor != null
            ArenaEntryPage.WALLET -> false
        }

    private suspend fun loadTickets(
        op: EntryOperation,
        append: Boolean,
    ) {
        val page = await(op) { deps.entries.localTickets(op.session, cursor = if (append) ticketsCursor else null) }
        ticketsCursor = page.nextCursor
        val rows = if (append) mutableState.value.tickets + page.entries else page.entries
        mutableState.value =
            mutableState.value.copy(
                tickets = rows.associateBy { it.ticketId }.values.toList(),
                canLoadTickets = ticketsCursor != null,
                hasLoadedTickets = true,
            )
    }

    private suspend fun loadPreparations(
        op: EntryOperation,
        append: Boolean,
    ) {
        val page =
            await(
                op,
            ) { deps.entries.pendingPreparations(op.session, cursor = if (append) preparationsCursor else null) }
        preparationsCursor = page.nextCursor
        val rows = if (append) mutableState.value.preparations + page.preparations else page.preparations
        mutableState.value =
            mutableState.value.copy(
                preparations = rows.associateBy { it.requestId }.values.toList(),
                canLoadPreparations = preparationsCursor != null,
                hasLoadedPreparations = true,
            )
    }

    private suspend fun reloadLocal(op: EntryOperation) {
        loadTickets(op, false)
        loadPreparations(op, false)
    }

    private fun applyOutcome(
        outcome: ArenaEntryCommandOutcome,
        requestId: String? = null,
    ) {
        if (outcome == ArenaEntryCommandOutcome.PRICE_CHANGED_REQUIRES_CONSENT && requestId != null) {
            blockedPreparations += requestId
        }
        notice(
            when (outcome) {
                ArenaEntryCommandOutcome.RECORDED,
                ArenaEntryCommandOutcome.ALREADY_RECORDED,
                -> ArenaEntryNotice.REQUEST_RECORDED
                ArenaEntryCommandOutcome.RETRY_SAME_REQUEST -> ArenaEntryNotice.RETRY_SAME_REQUEST
                ArenaEntryCommandOutcome.PRICE_CHANGED_REQUIRES_CONSENT -> ArenaEntryNotice.PRICE_CHANGED
            },
        )
    }

    private fun acceptResolution(result: ArenaEntryResolution) =
        notice(
            when (result) {
                is ArenaEntryResolution.CancelledResolved -> ArenaEntryNotice.CANCELLED_RESOLVED
                is ArenaEntryResolution.StillReserved -> ArenaEntryNotice.STILL_RESERVED
                is ArenaEntryResolution.SettledUnchanged -> ArenaEntryNotice.SETTLED_OBSERVED
            },
        )

    private fun start(
        stamp: ArenaSessionStamp,
        invalidateWallet: Boolean = false,
        requestId: String? = null,
        work: suspend (EntryOperation) -> Unit,
    ) {
        val op = EntryOperation(stamp)
        active = op
        mutableState.value =
            mutableState.value.copy(
                busy = true,
                notice = ArenaEntryNotice.READY,
                dialog = null,
                lastRequestId = requestId ?: mutableState.value.lastRequestId,
                wallet = if (invalidateWallet) null else mutableState.value.wallet,
            )
        val job = scope.launch(start = CoroutineStart.LAZY) { execute(op, work) }
        op.job = job
        if (active === op) job.start() else job.cancel()
    }

    private suspend fun execute(
        op: EntryOperation,
        work: suspend (EntryOperation) -> Unit,
    ) {
        try {
            ensureCurrent(op)
            work(op)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (
            @Suppress("TooGenericExceptionCaught") error: Exception,
        ) {
            if (owns(op)) notice(entryFailureNotice(error))
        } finally {
            if (active === op) {
                active = null
                mutableState.value = mutableState.value.copy(busy = false)
            }
        }
    }

    private suspend fun <T> await(
        op: EntryOperation,
        call: suspend () -> Result<T>,
    ): T {
        ensureCurrent(op)
        val result = call()
        ensureCurrent(op)
        return result.getOrThrow()
    }

    private suspend fun ensureCurrent(op: EntryOperation) {
        coroutineContext.ensureActive()
        if (!owns(op)) throw CancellationException("Arena entry epoch changed")
    }

    private fun owns(op: EntryOperation): Boolean = !destroyed && recheckSession() == op.session && active === op

    private fun notice(value: ArenaEntryNotice) {
        mutableState.value = mutableState.value.copy(notice = value)
    }

    private fun reset(actual: ArenaSessionStamp?) {
        val previous = active
        active = null
        session = actual
        ticketsCursor = null
        preparationsCursor = null
        recoveryCursor = null
        blockedPreparations.clear()
        mutableState.value =
            initial(actual).copy(
                notice = if (actual == null) ArenaEntryNotice.ACCOUNT_UNAVAILABLE else ArenaEntryNotice.ACCOUNT_CHANGED,
            )
        previous?.job?.cancel()
    }

    override fun onDestroy() {
        destroyed = true
        reset(null)
        scope.cancel()
    }

    private fun initial(stamp: ArenaSessionStamp?) =
        ArenaEntryUiState(
            session = stamp,
            notice = if (stamp == null) ArenaEntryNotice.ACCOUNT_UNAVAILABLE else ArenaEntryNotice.NOT_LOADED,
        )

    private class EntryOperation(val session: ArenaSessionStamp) {
        var job: Job? = null
    }

    private companion object {
        const val WALLET_MAX_AGE_MS = 60_000L
    }
}

private fun ArenaEntryDialog.Prepare.request(
    owner: String,
    requestId: String,
): ArenaPrepareRequest =
    if (lesson == null) {
        ArenaPrepareRequest.Random(owner, requestId, difficulty)
    } else {
        ArenaPrepareRequest.Selected(owner, requestId, difficulty, lesson.lessonId, lesson.priceNolics)
    }
