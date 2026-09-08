package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component

import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ArenaFreshStart
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ArenaRunnerRequest
import com.tpov.schoolquiz.shared.core.sync.SyncFrequency
import com.tpov.schoolquiz.shared.core.sync.SyncScheduler
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaEntriesCursor
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaPreparationsCursor
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaPrepareRequest
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRecoveryCursor
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaWalletObservation
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptIdentity
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ServerAttemptSettlement
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ServerAttemptSettlementState
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.SessionMode
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaEntryRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaRunDecision
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaRunRead
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionObserver
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionProvider
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionStamp
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaWalletRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ServerAttemptSettlementRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.assertEquals
import kotlin.test.assertIs

internal class ArenaRunHostFixture(scope: TestScope) {
    val run = RunCoordinatorFixture(scope)
    val sessionEvents = MutableStateFlow<ArenaSessionStamp?>(run.stamp)
    var walletCalls = 0
    var syncCalls = 0
    var beforeWallet: suspend () -> Unit = {}
    val entries = HostEntryFake(run)
    val settlements = HostSettlementFake()
    private val services =
        ArenaRunServices(
            run.repository,
            entries,
            ArenaSessionProvider { run.session },
            ArenaSessionObserver { sessionEvents },
            ArenaWalletRepository { stamp ->
                walletCalls++
                beforeWallet()
                Result.success(ArenaWalletObservation(stamp, run.now, 1, 0, 500, 50, 1000))
            },
            object : SyncScheduler {
                override fun enqueueManualSync() {
                    syncCalls++
                }

                override fun enqueueManualProfileSync() = error("Unexpected profile sync")

                override fun applyFrequency(frequency: SyncFrequency) = error("Unexpected schedule")

                override fun applyProfileFrequency(frequency: SyncFrequency) = error("Unexpected profile schedule")
            },
            settlements,
        )
    private val dispatcher = StandardTestDispatcher(scope.testScheduler)

    fun host(
        fresh: Boolean = true,
        initiatingSession: ArenaSessionStamp? = null,
    ): ArenaRunHost =
        ArenaRunHost(
            ArenaRunnerRequest(
                run.stamp.ownerUid,
                run.header.ticketId,
                if (fresh) ArenaFreshStart(run.stamp, SessionMode.LEARNING) else null,
                initiatingSession,
            ),
            services,
            object : Clock {
                override fun now(): Instant = Instant.fromEpochMilliseconds(run.now)
            },
            dispatcher,
        )

    fun switchEpoch() {
        run.session = run.stamp.copy(generation = 3)
        sessionEvents.value = run.session
    }
}

/** Tracks subscription lifetime so receipt tests prove that stale flows cannot paint a retained host. */
internal class HostSettlementFake : ServerAttemptSettlementRepository {
    private val values = mutableMapOf<AttemptIdentity, MutableStateFlow<ServerAttemptSettlement>>()
    val observedAttempts = mutableListOf<AttemptIdentity>()
    var cancellations = 0

    override fun observe(attempt: AttemptIdentity): Flow<ServerAttemptSettlement> =
        flow {
            observedAttempts += attempt
            try {
                valueFor(attempt).collect { emit(it) }
            } finally {
                cancellations++
            }
        }

    fun emit(
        attempt: AttemptIdentity,
        settlement: ServerAttemptSettlement,
    ) {
        valueFor(attempt).value = settlement
    }

    private fun valueFor(attempt: AttemptIdentity) =
        values.getOrPut(attempt) {
            MutableStateFlow(ServerAttemptSettlement(ServerAttemptSettlementState.PENDING))
        }
}

internal fun ArenaRunHost.question() = assertIs<ArenaRunnerView.Question>(view.value)

/** Every unrelated entry operation throws; cancellation tests cannot silently use a permissive fallback. */
internal class HostEntryFake(private val run: RunCoordinatorFixture) : ArenaEntryRepository {
    val cancels = mutableListOf<String>()
    var beforeCancel: suspend () -> Unit = {}
    var afterCancel: suspend () -> Unit = {}
    var dispatches = 0

    override suspend fun recordCancelIntent(
        stamp: ArenaSessionStamp,
        ticketId: String,
        requestId: String,
    ): Result<Unit> {
        assertEquals(run.stamp, stamp)
        assertEquals(run.header.ticketId, ticketId)
        beforeCancel()
        cancels += requestId
        run.observed = ArenaRunRead.DecisionPending(run.header, ArenaRunDecision.CANCEL)
        afterCancel()
        return Result.success(Unit)
    }

    override suspend fun dispatchCancel(
        stamp: ArenaSessionStamp,
        ticketId: String,
    ): Nothing {
        dispatches++
        error("Network must be requested explicitly in these tests")
    }

    override suspend fun localTickets(
        stamp: ArenaSessionStamp,
        size: Int,
        cursor: ArenaEntriesCursor?,
    ): Nothing = error("Unexpected local tickets")

    override suspend fun pendingPreparations(
        stamp: ArenaSessionStamp,
        size: Int,
        cursor: ArenaPreparationsCursor?,
    ): Nothing = error("Unexpected preparations")

    override suspend fun readTicket(
        stamp: ArenaSessionStamp,
        ticketId: String,
    ): Nothing =
        error(
            "Unexpected ticket read",
        )

    override suspend fun recordPrepareConsent(
        stamp: ArenaSessionStamp,
        request: ArenaPrepareRequest,
    ): Nothing = error("Unexpected preparation consent")

    override suspend fun dispatchPrepare(
        stamp: ArenaSessionStamp,
        requestId: String,
    ): Nothing =
        error(
            "Unexpected prepare",
        )

    override suspend fun recoverPage(
        stamp: ArenaSessionStamp,
        size: Int,
        cursor: ArenaRecoveryCursor?,
    ): Nothing = error("Unexpected page recovery")

    override suspend fun recoverTicket(
        stamp: ArenaSessionStamp,
        ticketId: String,
    ): Nothing =
        error(
            "Unexpected recovery",
        )

    override suspend fun reconcileCancelledSettlement(
        stamp: ArenaSessionStamp,
        ticketId: String,
    ): Nothing = error("Unexpected settlement reconciliation")

    override suspend fun restorePendingSettlement(
        stamp: ArenaSessionStamp,
        ticketId: String,
    ): Nothing = error("Unexpected settlement restore")
}
