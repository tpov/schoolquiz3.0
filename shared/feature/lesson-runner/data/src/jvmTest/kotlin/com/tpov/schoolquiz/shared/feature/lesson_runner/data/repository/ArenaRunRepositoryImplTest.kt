@file:Suppress("MagicNumber")
@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.tpov.schoolquiz.shared.feature.lesson_runner.data.repository

import com.tpov.schoolquiz.shared.core.outbox.OutboxSession
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.RunReadFixture
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.assertRunReadCancellation
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.arenaCheckpointToReady
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRunnerPhase
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaRunFailure
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaRunFailureKind
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaRunRead
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaRunRecoveryReason
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionProvider
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionStamp
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Test
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArenaRunRepositoryImplTest {
    private fun repository(
        f: RunReadFixture,
        sessions: ArenaSessionProvider =
            ArenaSessionProvider { f.gate.current.let { ArenaSessionStamp(it.ownerUid, it.generation) } },
    ) = ArenaRunRepositoryImpl(f.journal, f.checkpoints, sessions, f.dispatcher)

    @Test
    fun resolvingAndConstructingRepositoryHasNoActions() =
        runTest {
            val f = RunReadFixture(this)
            repository(f)
            f.assertReadOnly(0)
        }

    @Test
    fun absentAndPreparedAreDistinctHeaderOnlyDomainOutcomes() =
        runTest {
            val f = RunReadFixture(this)
            val repo = repository(f)
            val before = f.financialState()
            val result = assertIs<ArenaRunRead.Prepared>(repo.read(f.stamp, f.header.ticketId).getOrThrow())
            assertEquals(f.header, result.header)
            assertEquals(before, f.financialState())
            f.storage.tickets.clear()
            assertEquals(ArenaRunRead.LocalAbsent, repo.read(f.stamp, f.header.ticketId).getOrThrow())
            f.assertReadOnly(2)
        }

    @Test
    fun playableReadPreservesEveryBindingTimingAndIdentityFieldWithoutLiveStart() =
        runTest {
            val f = RunReadFixture(this)
            f.launch()
            val before = f.financialState()
            val saved = f.checkpoints.decode(requireNotNull(f.row.checkpointJson), f.binding)
            val result = assertIs<ArenaRunRead.Continuable>(repository(f).read(f.stamp, f.header.ticketId).getOrThrow())
            assertEquals(f.header.ownerUid, result.checkpoint.ownerUid)
            assertEquals(f.header.ticketId, result.checkpoint.ticketId)
            assertEquals(f.header.attemptId, result.checkpoint.attemptId)
            assertEquals(f.binding.snapshotSha256, result.checkpoint.snapshotSha256)
            assertEquals(f.ticket.snapshot, result.binding.snapshot)
            assertEquals(saved.seed, result.checkpoint.seed)
            assertEquals(saved.sessionMode, result.checkpoint.sessionMode)
            assertEquals(saved.indexInPool, result.checkpoint.indexInPool)
            assertEquals(saved.questionStartedAtMs, result.checkpoint.questionStartedAtMs)
            assertEquals(saved.deadlineMs, result.checkpoint.deadlineMs)
            assertEquals(saved.acceptedAnswers, result.checkpoint.acceptedAnswers)
            assertEquals(saved.chargeClaims, result.checkpoint.chargeClaims)
            assertEquals(saved.currentDraft, result.checkpoint.currentDraft)
            assertEquals(saved.pendingFeedback, result.checkpoint.pendingFeedback)
            assertEquals(saved.endedAtMs, result.checkpoint.endedAtMs)
            assertEquals(7L, result.revision)
            assertEquals(before, f.financialState())
            f.assertReadOnly(1)
        }

    @Test
    fun feedbackAndPausedRemainContinuationsWithSavedPhaseNotFreshInitialState() =
        runTest {
            for (phase in listOf(ArenaRunnerPhase.FEEDBACK, ArenaRunnerPhase.PAUSED)) {
                val f = RunReadFixture(this)
                f.launch(phase)
                val result =
                    assertIs<ArenaRunRead.Continuable>(repository(f).read(f.stamp, f.header.ticketId).getOrThrow())
                assertEquals(phase, result.checkpoint.phase)
                assertEquals(f.checkpoint(phase), result.checkpoint)
                if (phase == ArenaRunnerPhase.FEEDBACK) {
                    assertEquals(result.checkpoint.pendingFeedback!!.answer, result.checkpoint.currentDraft)
                }
                f.assertReadOnly(1)
            }
        }

    @Test
    fun completingIsSeparateAndKeepsOriginalFinalAnswersAndEndedAtWithoutFreezing() =
        runTest {
            val f = RunReadFixture(this)
            f.launch(ArenaRunnerPhase.COMPLETING)
            val before = f.financialState()
            val result = assertIs<ArenaRunRead.Completing>(repository(f).read(f.stamp, f.header.ticketId).getOrThrow())
            assertEquals(f.ticket.snapshot.questions.size, result.checkpoint.indexInPool)
            assertEquals(1_101L, result.checkpoint.endedAtMs)
            assertEquals(f.checkpoint(ArenaRunnerPhase.COMPLETING).acceptedAnswers, result.checkpoint.acceptedAnswers)
            assertEquals(7L, result.revision)
            assertEquals(before, f.financialState())
            f.assertReadOnly(1)
        }

    @Test
    fun hardContinuationNeverInventsScoredAnswersOrNumericZero() =
        runTest {
            val f = RunReadFixture(this, hard = true)
            f.launch(ArenaRunnerPhase.COMPLETING)
            val result = assertIs<ArenaRunRead.Completing>(repository(f).read(f.stamp, f.header.ticketId).getOrThrow())
            val restored = arenaCheckpointToReady(result.checkpoint, result.binding)
            assertNull(restored.codeAnswer)
            assertTrue(restored.answers.isEmpty())
            assertEquals(result.checkpoint.acceptedAnswers, restored.rawAnswers)
            assertEquals(f.header.attemptId, restored.attemptId.value)
            f.assertReadOnly(1)
        }

    @Test
    fun unsafePendingAndTerminalRemainNonPlayableWithoutDecodingTheirBank() =
        runTest {
            for (status in listOf("RESERVED", "CANCELLED", "SETTLED")) {
                val f = RunReadFixture(this)
                f.row =
                    f.row.copy(
                        localUse = "LAUNCHED",
                        decision = "SETTLE",
                        serverStatus = status,
                        snapshotJson = "unsafe raw content",
                        checkpointJson = "unsafe checkpoint",
                    )
                val before = f.financialState()
                val result = repository(f).read(f.stamp, f.header.ticketId).getOrThrow()
                if (status == "RESERVED") {
                    assertIs<ArenaRunRead.DecisionPending>(
                        result,
                    )
                } else {
                    assertIs<ArenaRunRead.Terminal>(result)
                }
                assertEquals(before, f.financialState())
                f.assertReadOnly(1)
            }
        }

    @Test
    fun malformedRecordAndMissingCheckpointDistinguishRecoveryFromLocalAbsence() =
        runTest {
            val f = RunReadFixture(this)
            val repo = repository(f)
            f.row = f.row.copy(localUse = "LAUNCHED")
            val missing = assertIs<ArenaRunRead.NeedsRecovery>(repo.read(f.stamp, f.header.ticketId).getOrThrow())
            assertEquals(f.header, missing.header)
            assertEquals(ArenaRunRecoveryReason.CHECKPOINT_UNAVAILABLE, missing.reason)
            f.row = f.row.copy(termsJson = "private invalid terms")
            val invalid = assertIs<ArenaRunRead.NeedsRecovery>(repo.read(f.stamp, f.header.ticketId).getOrThrow())
            assertNull(invalid.header)
            assertEquals(ArenaRunRecoveryReason.INVALID_RECORD, invalid.reason)
            f.assertReadOnly(2)
        }

    @Test
    fun invalidTicketIdFailsBeforeStorageAndDoesNotLeakInput() =
        runTest {
            val f = RunReadFixture(this)
            val failure = assertIs<ArenaRunFailure>(repository(f).read(f.stamp, "private/path").exceptionOrNull())
            assertEquals(ArenaRunFailureKind.INVALID_REQUEST, failure.kind)
            assertEquals("INVALID_REQUEST", failure.message)
            assertNull(failure.cause)
            f.assertReadOnly(0)
        }

    @Test
    fun signedOutOrForeignOrRenewedOwnerStampCannotRecaptureAndRead() =
        runTest {
            for (current in listOf(null, ArenaSessionStamp("other", 2), ArenaSessionStamp("player", 3))) {
                val f = RunReadFixture(this)
                val failure =
                    repository(
                        f,
                        ArenaSessionProvider { current },
                    ).read(f.stamp, f.header.ticketId).exceptionOrNull()
                assertEquals(ArenaRunFailureKind.ACCOUNT_CHANGED, assertIs<ArenaRunFailure>(failure).kind)
                f.assertReadOnly(0)
            }
        }

    @Test
    fun epochChangesWhileDispatcherQueuedBeforeDatabaseAccess() =
        runTest {
            val f = RunReadFixture(this)
            val work = async { repository(f).read(f.stamp, f.header.ticketId) }
            f.gate.current = OutboxSession("player", 3)
            runCurrent()
            assertEquals(
                ArenaRunFailureKind.ACCOUNT_CHANGED,
                assertIs<ArenaRunFailure>(work.await().exceptionOrNull()).kind,
            )
            f.assertReadOnly(0)
        }

    @Test
    fun postReadEpochChangeDiscardsResultAndUnknownStorageFailure() =
        runTest {
            for (throws in listOf(false, true)) {
                val f = RunReadFixture(this)
                f.afterRead = {
                    f.gate.current = OutboxSession("player", 3)
                    if (throws) error("private storage diagnostic")
                }
                val failure =
                    assertIs<ArenaRunFailure>(repository(f).read(f.stamp, f.header.ticketId).exceptionOrNull())
                assertEquals(ArenaRunFailureKind.ACCOUNT_CHANGED, failure.kind)
                assertNull(failure.cause)
                f.assertReadOnly(1)
            }
        }

    @Test
    fun epochChangeAfterJournalReturnsIsCheckedAgainBeforeTypedProjection() =
        runTest {
            val f = RunReadFixture(this)
            var checks = 0
            val provider =
                ArenaSessionProvider {
                    checks++
                    if (checks == 1) f.stamp else f.stamp.copy(generation = 3)
                }
            val failure =
                assertIs<ArenaRunFailure>(repository(f, provider).read(f.stamp, f.header.ticketId).exceptionOrNull())
            assertEquals(ArenaRunFailureKind.ACCOUNT_CHANGED, failure.kind)
            f.assertReadOnly(1)
        }

    @Test
    fun returnDispatchHopCannotPublishOldSameOwnerEpoch() =
        runTest {
            val f = RunReadFixture(this)
            var dispatched = 0
            val caller =
                object : CoroutineDispatcher() {
                    override fun dispatch(
                        context: CoroutineContext,
                        block: Runnable,
                    ) {
                        dispatched++
                        if (dispatched == 2) f.gate.current = OutboxSession("player", 3)
                        f.dispatcher.dispatch(context, block)
                    }
                }
            val result = withContext(caller) { repository(f).read(f.stamp, f.header.ticketId) }
            assertTrue(dispatched >= 2)
            assertEquals(ArenaRunFailureKind.ACCOUNT_CHANGED, assertIs<ArenaRunFailure>(result.exceptionOrNull()).kind)
            f.assertReadOnly(1)
        }

    @Test
    fun unknownStorageFailureBecomesSanitizedLocalFailureNotAbsenceOrTerminal() =
        runTest {
            val f = RunReadFixture(this)
            f.afterRead = { error("private database file and raw bank") }
            val before = f.financialState()
            val failure = assertIs<ArenaRunFailure>(repository(f).read(f.stamp, f.header.ticketId).exceptionOrNull())
            assertEquals(ArenaRunFailureKind.LOCAL_READ_FAILED, failure.kind)
            assertEquals("LOCAL_READ_FAILED", failure.message)
            assertNull(failure.cause)
            assertEquals(before, f.financialState())
            f.assertReadOnly(1)
        }

    @Test
    fun cancellationEscapesOriginalOrDirectRecoveryCopyNeverDomainFailure() =
        runTest {
            val f = RunReadFixture(this)
            val expected = CancellationException("cancel original read")
            f.afterRead = { throw expected }
            val actual = assertFailsWith<CancellationException> { repository(f).read(f.stamp, f.header.ticketId) }
            assertRunReadCancellation(expected, actual)
            f.assertReadOnly(1)
        }
}
