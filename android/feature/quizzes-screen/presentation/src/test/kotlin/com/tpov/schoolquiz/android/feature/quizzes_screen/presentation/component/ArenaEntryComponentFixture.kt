package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.instancekeeper.InstanceKeeperDispatcher
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.lifecycle.stop
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaEntriesCursor
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaEntriesPage
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaEntryCommandOutcome
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaEntryResolution
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLocalEntry
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaPreparationsCursor
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaPreparationsPage
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaPrepareRequest
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRecoveryCursor
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRecoveryPage
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaWalletObservation
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaEntryRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionObserver
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionProvider
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionStamp
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaWalletRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow

internal class ArenaEntryFixture(dispatcher: CoroutineDispatcher) : AutoCloseable {
    val lifecycle = LifecycleRegistry()
    val keeper = InstanceKeeperDispatcher()
    val sessions = EntrySessions()
    val repository = EntryRepository()
    var now = 1000L
    var walletCalls = 0
    var walletReply: suspend (ArenaSessionStamp) -> Result<ArenaWalletObservation> = {
        Result.success(ArenaWalletObservation(it, now, 70, 30, 8, 2, 500))
    }
    private var nextId = 0
    val dependencies =
        ArenaEntryDependencies(
            repository,
            ArenaWalletRepository { stamp ->
                walletCalls++
                walletReply(stamp)
            },
            sessions,
            sessions,
        )
    val component =
        DefaultArenaEntryComponent(
            DefaultComponentContext(lifecycle, instanceKeeper = keeper),
            dependencies,
            dispatcher,
            ids = { "entry-id-" + ++nextId },
            nowMs = { now },
        )
    val stamp get() = requireNotNull(sessions.current())
    val state get() = component.state.value

    init {
        lifecycle.resume()
    }

    fun load(
        page: ArenaEntryPage,
        append: Boolean = false,
    ) {
        component.onEvent(ArenaEntryEvent.Load(stamp, page, append))
    }

    fun confirm() {
        component.onEvent(ArenaEntryEvent.Confirm(stamp, requireNotNull(state.dialog).id))
    }

    override fun close() {
        keeper.destroy()
        lifecycle.stop()
        lifecycle.destroy()
    }
}

internal class EntrySessions : ArenaSessionProvider, ArenaSessionObserver {
    val events = MutableStateFlow<ArenaSessionStamp?>(ArenaSessionStamp("owner-a", 1))

    override fun current() = events.value

    override fun observeSessions() = events
}

internal class EntryRepository : ArenaEntryRepository {
    val calls = mutableListOf<String>()
    val requests = mutableListOf<ArenaPrepareRequest>()
    val ticketsCursors = mutableListOf<ArenaEntriesCursor?>()
    val preparationsCursors = mutableListOf<ArenaPreparationsCursor?>()
    val recoveryCursors = mutableListOf<ArenaRecoveryCursor?>()
    var tickets = ArenaEntriesPage(emptyList(), null)
    var preparations = ArenaPreparationsPage(emptyList(), null)
    var recovery = ArenaRecoveryPage(emptyList(), null)
    var recordHook: suspend () -> Unit = {}
    var dispatchHook: suspend () -> Result<ArenaEntryCommandOutcome> = {
        Result.success(ArenaEntryCommandOutcome.RECORDED)
    }
    var ticketsHook: suspend () -> Result<ArenaEntriesPage> = { Result.success(tickets) }
    var resolution: ArenaEntryResolution? = null

    override suspend fun localTickets(
        stamp: ArenaSessionStamp,
        size: Int,
        cursor: ArenaEntriesCursor?,
    ): Result<ArenaEntriesPage> {
        check(size == 20)
        calls += "tickets"
        ticketsCursors += cursor
        return ticketsHook()
    }

    override suspend fun pendingPreparations(
        stamp: ArenaSessionStamp,
        size: Int,
        cursor: ArenaPreparationsCursor?,
    ): Result<ArenaPreparationsPage> {
        check(size == 20)
        calls += "preparations"
        preparationsCursors += cursor
        return Result.success(preparations)
    }

    override suspend fun readTicket(
        stamp: ArenaSessionStamp,
        ticketId: String,
    ): Result<ArenaLocalEntry?> {
        calls += "read:$ticketId"
        return Result.success(tickets.entries.find { it.ticketId == ticketId })
    }

    override suspend fun recordPrepareConsent(
        stamp: ArenaSessionStamp,
        request: ArenaPrepareRequest,
    ): Result<Unit> {
        check(stamp.ownerUid == request.ownerUid)
        calls += "record:" + request.requestId
        requests += request
        recordHook()
        return Result.success(Unit)
    }

    override suspend fun dispatchPrepare(
        stamp: ArenaSessionStamp,
        requestId: String,
    ): Result<ArenaEntryCommandOutcome> {
        calls += "dispatch:$requestId"
        return dispatchHook()
    }

    override suspend fun recoverPage(
        stamp: ArenaSessionStamp,
        size: Int,
        cursor: ArenaRecoveryCursor?,
    ): Result<ArenaRecoveryPage> {
        check(size == 20)
        calls += "recover-page"
        recoveryCursors += cursor
        return Result.success(recovery)
    }

    override suspend fun recoverTicket(
        stamp: ArenaSessionStamp,
        ticketId: String,
    ): Result<ArenaLocalEntry> {
        calls += "recover:$ticketId"
        return Result.success(tickets.entries.first { it.ticketId == ticketId })
    }

    override suspend fun recordCancelIntent(
        stamp: ArenaSessionStamp,
        ticketId: String,
        requestId: String,
    ): Result<Unit> {
        calls += "cancel-record:$ticketId:$requestId"
        recordHook()
        return Result.success(Unit)
    }

    override suspend fun dispatchCancel(
        stamp: ArenaSessionStamp,
        ticketId: String,
    ): Result<ArenaEntryCommandOutcome> {
        calls += "cancel-dispatch:$ticketId"
        return dispatchHook()
    }

    override suspend fun reconcileCancelledSettlement(
        stamp: ArenaSessionStamp,
        ticketId: String,
    ): Result<ArenaEntryResolution> {
        calls += "reconcile:$ticketId"
        return Result.success(requireNotNull(resolution))
    }

    override suspend fun restorePendingSettlement(
        stamp: ArenaSessionStamp,
        ticketId: String,
    ): Result<Unit> {
        calls += "restore:$ticketId"
        return Result.success(Unit)
    }
}
