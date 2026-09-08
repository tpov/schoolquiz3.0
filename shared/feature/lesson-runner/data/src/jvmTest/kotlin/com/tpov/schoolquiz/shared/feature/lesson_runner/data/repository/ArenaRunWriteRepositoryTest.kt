@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.tpov.schoolquiz.shared.feature.lesson_runner.data.repository

import com.tpov.schoolquiz.shared.core.network.NetworkMonitor
import com.tpov.schoolquiz.shared.core.outbox.OutboxSession
import com.tpov.schoolquiz.shared.core.persistence.ArenaCheckpointValidator
import com.tpov.schoolquiz.shared.core.persistence.ArenaCommandEntity
import com.tpov.schoolquiz.shared.core.persistence.OutboxEntity
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.ArenaJournalCheckpointCodec
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.ArenaJournalStorage
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.ArenaTicketJournal
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.RunReadFixture
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.arenaCheckpointToReady
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computeTimer
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaInitialCheckpoint
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRunnerPhase
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketSnapshot
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.SessionMode
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.TimerCoefficients
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaRunCasOutcome
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaRunClaimOutcome
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaRunFailure
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaRunFailureKind
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaRunFinalizeOutcome
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaRunRead
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionProvider
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionStamp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.Test
import kotlin.coroutines.CoroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArenaRunWriteRepositoryTest {
    @Test
    fun pausedResumePreservesStartedAtAndModeAndChangesOnlyAllowedDeadline() =
        runTest {
            val f = WriteFixture(this)
            f.base.launch(ArenaRunnerPhase.PAUSED)
            val paused = f.current()
            val resumed = paused.checkpoint.copy(phase = ArenaRunnerPhase.ANSWERING, deadlineMs = 90000)
            val outcome =
                assertIs<ArenaRunCasOutcome.Saved>(
                    f.repository.checkpointCAS(f.base.stamp, f.id, paused.revision, resumed).getOrThrow(),
                )
            val current = assertIs<ArenaRunRead.Continuable>(outcome.current)
            assertEquals(resumed, current.checkpoint)
            assertEquals(paused.revision + 1, current.revision)
            assertEquals(paused.checkpoint.questionStartedAtMs, current.checkpoint.questionStartedAtMs)
            assertEquals(paused.checkpoint.sessionMode, current.checkpoint.sessionMode)
            assertEquals(0, f.initialCalls)
            assertEquals(0, f.clockCalls)
        }

    @Test
    fun pauseCannotSilentlySkipClosingCurrentAnsweringQuestion() =
        runTest {
            val f = WriteFixture(this)
            f.fresh()
            val initial = f.current()
            val before = f.base.financialState()
            assertTrue(
                f.repository.checkpointCAS(
                    f.base.stamp,
                    f.id,
                    initial.revision,
                    initial.checkpoint.copy(phase = ArenaRunnerPhase.PAUSED),
                ).isFailure,
            )
            assertEquals(before, f.base.financialState())
        }

    @Test
    fun feedbackIsSavedBeforeReturnAndLockedAnswerCannotDisappear() =
        runTest {
            val f = WriteFixture(this)
            f.fresh()
            val feedback = f.base.checkpoint(ArenaRunnerPhase.FEEDBACK)
            val outcome =
                assertIs<ArenaRunCasOutcome.Saved>(
                    f.repository.checkpointCAS(f.base.stamp, f.id, 0, feedback).getOrThrow(),
                )
            val current = assertIs<ArenaRunRead.Continuable>(outcome.current)
            assertEquals(feedback.pendingFeedback, current.checkpoint.pendingFeedback)
            assertEquals(feedback.currentDraft, current.checkpoint.currentDraft)
            assertEquals(feedback.chargeClaims, current.checkpoint.chargeClaims)
            val before = f.base.financialState()
            assertTrue(f.repository.checkpointCAS(f.base.stamp, f.id, 1, feedback.copy(currentDraft = null)).isFailure)
            assertTrue(f.repository.checkpointCAS(f.base.stamp, f.id, 1, feedback.copy(seed = 92)).isFailure)
            assertEquals(before, f.base.financialState())
        }

    @Test
    fun returnDispatcherEpochChangeCannotPublishSuccessfulOldClaim() =
        runTest {
            val f = WriteFixture(this)
            var dispatches = 0
            val caller =
                object : CoroutineDispatcher() {
                    override fun dispatch(
                        context: CoroutineContext,
                        block: Runnable,
                    ) {
                        dispatches++
                        if (dispatches == 2) f.base.gate.current = OutboxSession("player", 3)
                        f.base.dispatcher.dispatch(context, block)
                    }
                }
            val result =
                withContext(caller) {
                    f.repository.freshClaim(f.base.stamp, f.id, SessionMode.LEARNING)
                }
            assertTrue(dispatches >= 2)
            assertEquals(ArenaRunFailureKind.ACCOUNT_CHANGED, assertIs<ArenaRunFailure>(result.exceptionOrNull()).kind)
            assertEquals("LAUNCHED", f.base.row.localUse)
            assertEquals(1, f.claimCalls)
        }

    @Test
    fun firstCancellationWinsOverPreviouslyReadCompleting() =
        runTest {
            val f = WriteFixture(this)
            f.fresh()
            val saved =
                assertIs<ArenaRunCasOutcome.Saved>(
                    f.repository.checkpointCAS(
                        f.base.stamp,
                        f.id,
                        0,
                        f.base.checkpoint(ArenaRunnerPhase.COMPLETING),
                    ).getOrThrow(),
                )
            val completing = assertIs<ArenaRunRead.Completing>(saved.current)
            f.journal.requestCancel(f.base.original, f.id, "cancel-original").getOrThrow()
            val before = f.base.financialState()
            assertTrue(f.repository.finalize(f.base.stamp, completing).isFailure)
            assertEquals(before, f.base.financialState())
            assertEquals("CANCEL", f.base.row.decision)
            assertTrue(f.base.storage.outbox.isEmpty())
        }

    @Test
    fun freshClaimCommitsInitialOnceAndDuplicateDoesNotResetTimerSeedOrRevision() =
        runTest {
            val f = WriteFixture(this)
            val first = assertIs<ArenaRunClaimOutcome.Claimed>(f.fresh(SessionMode.EXAM))
            val current = assertIs<ArenaRunRead.Continuable>(first.current)
            val seconds =
                computeTimer(
                    f.base.ticket.snapshot.questions.first().display,
                    f.base.header.difficulty,
                    TimerCoefficients.Default,
                    SessionMode.EXAM,
                ).seconds
            assertEquals(1000L, current.checkpoint.questionStartedAtMs)
            assertEquals(1000L + seconds * 1000L, current.checkpoint.deadlineMs)
            assertEquals(91L, current.checkpoint.seed)
            assertEquals(0L, current.revision)
            assertEquals("LAUNCHED", f.base.row.localUse)
            val before = f.base.financialState()
            f.now = 9000
            val duplicate = assertIs<ArenaRunClaimOutcome.NotFresh>(f.fresh())
            assertEquals(current, duplicate.current)
            assertEquals(before, f.base.financialState())
            assertEquals(1, f.initialCalls)
            assertEquals(1, f.claimCalls)
            assertEquals(2, f.clockCalls)
            assertEquals(2, f.seedCalls)
            assertTrue(f.base.storage.commands.isEmpty() && f.base.storage.outbox.isEmpty())
        }

    @Test
    fun resumeOnlyDoesNotCaptureTimeSeedOrClaimAndNeverCreatesMissingCheckpoint() =
        runTest {
            val f = WriteFixture(this)
            assertIs<ArenaRunRead.Prepared>(f.repository.resumeOnly(f.base.stamp, f.id).getOrThrow())
            f.base.row = f.base.row.copy(localUse = "LAUNCHED")
            assertIs<ArenaRunRead.NeedsRecovery>(f.repository.resumeOnly(f.base.stamp, f.id).getOrThrow())
            assertEquals(0, f.initialCalls)
            assertEquals(0, f.claimCalls)
            assertEquals(0, f.clockCalls)
            assertEquals(0, f.seedCalls)
        }

    @Test
    fun lostClaimResponseLeavesDurableLaunchAndRetryIsNotFresh() =
        runTest {
            val f = WriteFixture(this)
            f.afterClaim = { error("lost local response") }
            assertTrue(f.repository.freshClaim(f.base.stamp, f.id, SessionMode.LEARNING).isFailure)
            val saved = f.base.row
            f.afterClaim = {}
            assertIs<ArenaRunClaimOutcome.NotFresh>(f.fresh())
            assertEquals(saved, f.base.row)
            assertEquals(1, f.initialCalls)
            assertEquals(1, f.claimCalls)
        }

    @Test
    fun corruptFreshRowsNeverCallInitialFactoryOrWrite() =
        runTest {
            val mutations: List<(WriteFixture) -> Unit> =
                listOf(
                    { it.base.row = it.base.row.copy(checkpointRevision = 7) },
                    { it.base.row = it.base.row.copy(terminalJson = "{}") },
                    { it.base.row = it.base.row.copy(termsJson = " " + it.base.row.termsJson) },
                    { it.base.row = it.base.row.copy(localUse = "UNKNOWN") },
                    { it.base.row = it.base.row.copy(snapshotJson = "{}") },
                )
            for (mutate in mutations) {
                val f = WriteFixture(this)
                mutate(f)
                val before = f.base.financialState()
                val result = f.repository.freshClaim(f.base.stamp, f.id, SessionMode.LEARNING)
                assertFalse(result.getOrNull() is ArenaRunClaimOutcome.Claimed)
                assertEquals(before, f.base.financialState())
                assertEquals(0, f.initialCalls)
                assertEquals(0, f.claimCalls)
            }
        }

    @Test
    fun candidateIdentityHashAndWrongRevisionCannotOverwriteCurrentState() =
        runTest {
            val f = WriteFixture(this)
            val current = assertIs<ArenaRunRead.Continuable>(assertIs<ArenaRunClaimOutcome.Claimed>(f.fresh()).current)
            val before = f.base.financialState()
            val paused = current.checkpoint.copy(phase = ArenaRunnerPhase.PAUSED)
            for (candidate in listOf(
                paused.copy(ownerUid = "foreign"),
                paused.copy(ticketId = "f".repeat(64)),
                paused.copy(attemptId = "another-attempt"),
                paused.copy(snapshotSha256 = "f".repeat(64)),
            )) {
                assertTrue(f.repository.checkpointCAS(f.base.stamp, f.id, 0, candidate).isFailure)
                assertEquals(before, f.base.financialState())
            }
            assertIs<ArenaRunCasOutcome.Conflict>(
                f.repository.checkpointCAS(f.base.stamp, f.id, 7, paused).getOrThrow(),
            )
            assertTrue(f.repository.checkpointCAS(f.base.stamp, f.id, -1, paused).isFailure)
            assertTrue(f.repository.checkpointCAS(f.base.stamp, f.id, Long.MAX_VALUE, paused).isFailure)
            assertEquals(before, f.base.financialState())
            assertEquals(0, f.saveCalls)
        }

    @Test
    fun lostCasResponseIsAlreadyCurrentOnlyForExactSavedBodyAndBinding() =
        runTest {
            val f = WriteFixture(this)
            val start = assertIs<ArenaRunRead.Continuable>(assertIs<ArenaRunClaimOutcome.Claimed>(f.fresh()).current)
            val feedback = f.base.checkpoint(ArenaRunnerPhase.FEEDBACK)
            f.afterSave = { error("lost save response") }
            assertTrue(f.repository.checkpointCAS(f.base.stamp, f.id, 0, feedback).isFailure)
            f.afterSave = {}
            val accepted =
                assertIs<ArenaRunCasOutcome.AlreadyCurrent>(
                    f.repository.checkpointCAS(f.base.stamp, f.id, 0, feedback).getOrThrow(),
                )
            assertEquals(1L, assertIs<ArenaRunRead.Continuable>(accepted.current).revision)
            assertEquals(1, f.saveCalls)
            val competing = start.checkpoint
            assertIs<ArenaRunCasOutcome.Conflict>(
                f.repository.checkpointCAS(f.base.stamp, f.id, 0, competing).getOrThrow(),
            )
            assertEquals(1, f.saveCalls)
            assertEquals(feedback, f.current().checkpoint)
        }

    @Test
    fun falseCasReReadsWithoutBlindOverwriteOrInventedRevision() =
        runTest {
            val f = WriteFixture(this)
            f.fresh()
            val feedback = f.base.checkpoint(ArenaRunnerPhase.FEEDBACK)
            f.beforeSave = { f.base.row = f.base.row.copy(decision = "CANCEL") }
            val outcome =
                assertIs<ArenaRunCasOutcome.Conflict>(
                    f.repository.checkpointCAS(f.base.stamp, f.id, 0, feedback).getOrThrow(),
                )
            assertIs<ArenaRunRead.DecisionPending>(outcome.current)
            assertEquals(0L, f.base.row.checkpointRevision)
            assertEquals(1, f.saveCalls)
        }

    @Test
    fun changedImmutableTermsBetweenReadAndTxRejectsWrite() =
        runTest {
            val f = WriteFixture(this)
            f.fresh()
            val feedback = f.base.checkpoint(ArenaRunnerPhase.FEEDBACK)
            val checkpoint = f.base.row.checkpointJson
            f.beforeSave = { f.base.row = f.base.row.copy(termsJson = " " + f.base.row.termsJson) }
            assertTrue(f.repository.checkpointCAS(f.base.stamp, f.id, 0, feedback).isFailure)
            assertEquals(checkpoint, f.base.row.checkpointJson)
            assertEquals(0L, f.base.row.checkpointRevision)
        }

    @Test
    fun finalCasSurvivesRestartThenFreezeAndEvictedReplayNeverEnqueueAgainOrResetBackoff() =
        runTest {
            val f = WriteFixture(this, hard = true)
            f.fresh()
            val final = f.base.checkpoint(ArenaRunnerPhase.COMPLETING)
            val saved =
                assertIs<ArenaRunCasOutcome.Saved>(
                    f.repository.checkpointCAS(f.base.stamp, f.id, 0, final).getOrThrow(),
                )
            val completing = assertIs<ArenaRunRead.Completing>(saved.current)
            assertNull(arenaCheckpointToReady(completing.checkpoint, completing.binding).codeAnswer)
            val restored = assertIs<ArenaRunRead.Completing>(f.repository.resumeOnly(f.base.stamp, f.id).getOrThrow())
            assertEquals(completing, restored)
            f.afterFreeze = { error("lost freeze response") }
            assertTrue(f.repository.finalize(f.base.stamp, restored).isFailure)
            assertEquals(1, f.base.storage.outbox.size)
            assertEquals(1, f.base.storage.firstIntents.size)
            val originalQueue = f.base.storage.outbox.values.single()
            val backedOff = originalQueue.copy(attemptCount = 4, nextRetryAtMs = 9000)
            f.base.storage.outbox[backedOff.mutationId] = backedOff
            f.afterFreeze = {}
            f.base.row = f.base.row.copy(serverStatus = "SETTLED")
            assertTrue(f.journal.evictInactiveSnapshot(f.base.original, f.id).getOrThrow())
            assertNull(f.base.row.snapshotJson)
            val before = f.base.financialState()
            assertEquals(
                ArenaRunFinalizeOutcome.FrozenLocally(f.id, f.base.header.attemptId),
                f.repository.finalize(f.base.stamp, restored).getOrThrow(),
            )
            assertEquals(before, f.base.financialState())
            assertEquals(1, f.freezeCalls)
            assertEquals(1, f.replayCalls)
            assertEquals(backedOff, f.base.storage.outbox.values.single())
        }

    @Test
    fun completingCannotBeChangedOrRecreatedFromAnswering() =
        runTest {
            val f = WriteFixture(this)
            f.fresh()
            val initial = f.current()
            val forged = ArenaRunRead.Completing(initial.binding, initial.checkpoint, initial.revision)
            assertTrue(f.repository.finalize(f.base.stamp, forged).isFailure)
            val final = f.base.checkpoint(ArenaRunnerPhase.COMPLETING)
            f.repository.checkpointCAS(f.base.stamp, f.id, 0, final).getOrThrow()
            val before = f.base.financialState()
            assertTrue(f.repository.checkpointCAS(f.base.stamp, f.id, 1, final.copy(endedAtMs = 1102)).isFailure)
            assertEquals(before, f.base.financialState())
            assertTrue(f.base.storage.outbox.isEmpty())
        }

    @Test
    fun staleSameUidEpochBeforeDispatchDoesNotClaimOrCaptureClock() =
        runTest {
            val f = WriteFixture(this)
            f.base.gate.current = OutboxSession("player", 3)
            val error =
                assertIs<ArenaRunFailure>(
                    f.repository.freshClaim(f.base.stamp, f.id, SessionMode.LEARNING).exceptionOrNull(),
                )
            assertEquals(ArenaRunFailureKind.ACCOUNT_CHANGED, error.kind)
            assertEquals(0, f.clockCalls)
            assertEquals(0, f.claimCalls)
        }

    @Test
    fun epochSwitchAfterCommitDoesNotRollbackOrReturnPlayableOldState() =
        runTest {
            val f = WriteFixture(this)
            f.afterClaim = { f.base.gate.current = OutboxSession("player", 3) }
            val error =
                assertIs<ArenaRunFailure>(
                    f.repository.freshClaim(f.base.stamp, f.id, SessionMode.LEARNING).exceptionOrNull(),
                )
            assertEquals(ArenaRunFailureKind.ACCOUNT_CHANGED, error.kind)
            assertEquals("LAUNCHED", f.base.row.localUse)
            assertEquals(1, f.claimCalls)
            assertTrue(f.base.storage.outbox.isEmpty())
        }

    @Test
    fun coroutineCancellationAfterCommitPropagatesAndLeavesOriginalLaunch() =
        runTest {
            val f = WriteFixture(this)
            f.afterClaim = { throw CancellationException("after commit") }
            val task = async { f.repository.freshClaim(f.base.stamp, f.id, SessionMode.LEARNING) }
            runCurrent()
            try {
                task.await()
                error("Cancellation must escape")
            } catch (_: CancellationException) {
            }
            assertEquals("LAUNCHED", f.base.row.localUse)
            assertEquals(1, f.initialCalls)
            assertTrue(f.base.storage.outbox.isEmpty())
        }
}

private class WriteFixture(scope: TestScope, hard: Boolean = false) {
    val base = RunReadFixture(scope, hard)
    val id get() = base.header.ticketId
    var now = 1000L
    var clockCalls = 0
    var seedCalls = 0
    var initialCalls = 0
    var claimCalls = 0
    var saveCalls = 0
    var freezeCalls = 0
    var replayCalls = 0
    var afterClaim: suspend () -> Unit = {}
    var beforeSave: suspend () -> Unit = {}
    var afterSave: suspend () -> Unit = {}
    var afterFreeze: suspend () -> Unit = {}
    private val storage =
        object : ArenaJournalStorage by base.storage {
            override suspend fun claim(
                owner: String,
                ticket: String,
                checkpoint: String,
                validator: ArenaCheckpointValidator,
            ): Boolean {
                claimCalls++
                return base.storage.claim(owner, ticket, checkpoint, validator).also { afterClaim() }
            }

            override suspend fun saveCheckpoint(
                owner: String,
                ticket: String,
                attemptId: String,
                expectedRevision: Long,
                candidate: String,
                validator: ArenaCheckpointValidator,
            ): Boolean {
                saveCalls++
                beforeSave()
                return base.storage.saveCheckpoint(
                    owner,
                    ticket,
                    attemptId,
                    expectedRevision,
                    candidate,
                    validator,
                ).also {
                    afterSave()
                }
            }

            override suspend fun freezeSettlement(
                cmd: ArenaCommandEntity,
                row: OutboxEntity,
                validator: ArenaCheckpointValidator,
            ) {
                freezeCalls++
                base.storage.freezeSettlement(cmd, row, validator)
                afterFreeze()
            }

            override suspend fun confirmSettlementReplay(
                cmd: ArenaCommandEntity,
                row: OutboxEntity,
            ) {
                replayCalls++
                base.storage.confirmSettlementReplay(cmd, row)
            }
        }
    private val codec =
        object : ArenaJournalCheckpointCodec by base.checkpoints {
            override fun initial(
                snapshot: ArenaTicketSnapshot,
                snapshotSha256: String,
                request: ArenaInitialCheckpoint,
            ): String {
                initialCalls++
                return base.checkpoints.initial(snapshot, snapshotSha256, request)
            }
        }
    val journal =
        ArenaTicketJournal(
            storage,
            base.remote,
            base.gate,
            object : NetworkMonitor {
                override fun observeOnline() = flowOf(false)

                override suspend fun isOnline(): Boolean = error("Local writes must not check the network")
            },
            base.journalCodec,
            codec,
            { now },
            base.dispatcher,
        )
    val repository =
        ArenaRunRepositoryImpl(
            journal,
            base.checkpoints,
            ArenaSessionProvider { base.gate.current.let { ArenaSessionStamp(it.ownerUid, it.generation) } },
            base.dispatcher,
            object : Clock {
                override fun now(): Instant {
                    clockCalls++
                    return Instant.fromEpochMilliseconds(now)
                }
            },
            {
                seedCalls++
                91L
            },
        )

    suspend fun fresh(mode: SessionMode = SessionMode.LEARNING) =
        repository.freshClaim(base.stamp, id, mode).getOrThrow()

    suspend fun current() = assertIs<ArenaRunRead.Continuable>(repository.read(base.stamp, id).getOrThrow())
}
