@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component

import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.core.question_schema.OptionId
import com.tpov.schoolquiz.shared.core.question_schema.QuestionContent
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computeTimer
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.initialArenaRunnerCheckpoint
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.validateArenaRunnerTransition
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaEntryMode
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaInitialCheckpoint
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRunnerCheckpoint
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRunnerCheckpointBinding
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRunnerPhase
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketHeader
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketQuestion
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketSnapshot
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketStatus
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.SessionMode
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.TimerCoefficients
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaRunCasOutcome
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaRunClaimOutcome
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaRunRead
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaRunRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionProvider
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionStamp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArenaRunCoordinatorTest {
    @Test
    fun cancelledSaveAfterCommitRetainsDataAndRequiresReadRatherThanNewStart() =
        runTest {
            val f = RunCoordinatorFixture(this)
            f.start()
            f.afterCas = { throw CancellationException("after commit") }
            f.coordinator.dispatch(requireNotNull(f.coordinator.token()), ArenaRunAction.Answer(f.answer))
            runCurrent()
            assertEquals(ArenaRunNotice.RELOAD_REQUIRED, f.coordinator.state.value.notice)
            assertFalse(f.coordinator.state.value.busy)
            assertEquals(ArenaRunnerPhase.FEEDBACK, assertIs<ArenaRunRead.Continuable>(f.observed).checkpoint.phase)
            f.coordinator.reload()
            runCurrent()
            assertEquals(ArenaRunnerPhase.FEEDBACK, f.current().checkpoint.phase)
            assertEquals(1, f.candidates.size)
            assertEquals(1, f.freshCalls)
        }

    @Test
    fun constructorHasNoIoAndStartIsConsumedOnceBeforeSuspension() =
        runTest {
            val f = RunCoordinatorFixture(this)
            assertEquals(0, f.freshCalls + f.readCalls)
            f.coordinator.start(ArenaRunStart.Fresh(SessionMode.LEARNING))
            f.coordinator.start(ArenaRunStart.Fresh(SessionMode.EXAM))
            assertNull(f.coordinator.state.value.observed)
            assertTrue(f.coordinator.state.value.busy)
            runCurrent()
            assertEquals(1, f.freshCalls)
            assertIs<ArenaRunRead.Continuable>(f.coordinator.state.value.observed)
        }

    @Test
    fun restoredPreparedRouteNeverBecomesFresh() =
        runTest {
            val f = RunCoordinatorFixture(this)
            f.coordinator.start(ArenaRunStart.ResumeOnly)
            runCurrent()
            assertIs<ArenaRunRead.Prepared>(f.coordinator.state.value.observed)
            assertNull(f.coordinator.token())
            f.coordinator.reload()
            runCurrent()
            assertEquals(0, f.freshCalls)
            assertEquals(2, f.readCalls)
        }

    @Test
    fun feedbackWaitsForCasAndDoubleTapTimeoutAndBackgroundCannotQueueAnotherAnswer() =
        runTest {
            val f = RunCoordinatorFixture(this)
            f.start()
            val old = f.coordinator.state.value.observed
            val token = requireNotNull(f.coordinator.token())
            val wait = CompletableDeferred<Unit>()
            f.beforeCas = { wait.await() }
            f.coordinator.dispatch(token, ArenaRunAction.Answer(f.answer))
            f.coordinator.dispatch(token, ArenaRunAction.Answer(f.answer))
            f.coordinator.dispatch(token, ArenaRunAction.Timeout)
            f.coordinator.dispatch(token, ArenaRunAction.Background)
            runCurrent()
            assertEquals(old, f.coordinator.state.value.observed)
            assertEquals(1, f.candidates.size)
            assertNull(f.coordinator.token())
            wait.complete(Unit)
            runCurrent()
            assertEquals(ArenaRunnerPhase.FEEDBACK, f.current().checkpoint.phase)
            f.coordinator.dispatch(token, ArenaRunAction.Continue)
            runCurrent()
            assertEquals(1, f.candidates.size)
        }

    @Test
    fun failedSaveDoesNotRevealCandidateAndOnlyExplicitReadUnlocks() =
        runTest {
            val f = RunCoordinatorFixture(this)
            f.start()
            val previous = f.coordinator.state.value.observed
            f.beforeCas = { error("private storage failure") }
            f.coordinator.dispatch(requireNotNull(f.coordinator.token()), ArenaRunAction.Answer(f.answer))
            runCurrent()
            assertEquals(previous, f.coordinator.state.value.observed)
            assertEquals(ArenaRunNotice.RELOAD_REQUIRED, f.coordinator.state.value.notice)
            assertNull(f.coordinator.token())
            f.coordinator.reload()
            runCurrent()
            assertTrue(f.coordinator.token() != null)
            assertEquals(1, f.candidates.size)
            assertEquals(1, f.freshCalls)
        }

    @Test
    fun lostCasReplyReloadsCommittedFeedbackWithoutRetryingWrite() =
        runTest {
            val f = RunCoordinatorFixture(this)
            f.start()
            f.afterCas = { error("response lost after commit") }
            f.coordinator.dispatch(requireNotNull(f.coordinator.token()), ArenaRunAction.Answer(f.answer))
            runCurrent()
            assertEquals(ArenaRunNotice.RELOAD_REQUIRED, f.coordinator.state.value.notice)
            assertEquals(ArenaRunnerPhase.FEEDBACK, assertIs<ArenaRunRead.Continuable>(f.observed).checkpoint.phase)
            f.coordinator.reload()
            runCurrent()
            assertEquals(ArenaRunnerPhase.FEEDBACK, f.current().checkpoint.phase)
            assertEquals(1, f.candidates.size)
            assertEquals(1, f.freshCalls)
        }

    @Test
    fun continueCapturesTimeBeforeFirstDispatcherSuspension() =
        runTest {
            val f = RunCoordinatorFixture(this)
            f.start()
            f.coordinator.dispatch(requireNotNull(f.coordinator.token()), ArenaRunAction.Answer(f.answer))
            runCurrent()
            f.now = 2000
            f.coordinator.dispatch(requireNotNull(f.coordinator.token()), ArenaRunAction.Continue)
            f.now = 9000
            runCurrent()
            assertEquals(2000L, f.current().checkpoint.acceptedAnswers.single().answeredAtMs)
            assertEquals(1000L, f.current().checkpoint.acceptedAnswers.single().durationMs)
        }

    @Test
    fun sameUidNewEpochAfterCommitCannotPublishOldStateOrAcceptOldToken() =
        runTest {
            val f = RunCoordinatorFixture(this)
            f.start()
            val token = requireNotNull(f.coordinator.token())
            f.afterCas = { f.session = f.stamp.copy(generation = 3) }
            f.coordinator.dispatch(token, ArenaRunAction.Answer(f.answer))
            runCurrent()
            assertEquals(ArenaRunNotice.ACCOUNT_CHANGED, f.coordinator.state.value.notice)
            assertNull(f.coordinator.state.value.observed)
            assertEquals(ArenaRunnerPhase.FEEDBACK, assertIs<ArenaRunRead.Continuable>(f.observed).checkpoint.phase)
            f.coordinator.dispatch(token, ArenaRunAction.Continue)
            runCurrent()
            assertEquals(1, f.candidates.size)
        }

    @Test
    fun epochChangeBeforeDispatchMakesNoRepositoryCall() =
        runTest {
            val f = RunCoordinatorFixture(this)
            f.session = f.stamp.copy(generation = 3)
            f.coordinator.start(ArenaRunStart.Fresh(SessionMode.LEARNING))
            runCurrent()
            assertEquals(0, f.freshCalls + f.readCalls)
            assertEquals(ArenaRunNotice.ACCOUNT_CHANGED, f.coordinator.state.value.notice)
        }

    @Test
    fun conflictCanReturnTerminalButNeverPlayableOrAutomaticRetry() =
        runTest {
            val f = RunCoordinatorFixture(this)
            f.start()
            val token = requireNotNull(f.coordinator.token())
            f.beforeCas = { f.observed = ArenaRunRead.Terminal(f.header.copy(status = ArenaTicketStatus.CANCELLED)) }
            f.coordinator.dispatch(token, ArenaRunAction.Answer(f.answer))
            runCurrent()
            assertIs<ArenaRunRead.Terminal>(f.coordinator.state.value.observed)
            assertEquals(ArenaRunNotice.CONFLICT, f.coordinator.state.value.notice)
            assertNull(f.coordinator.token())
            assertEquals(1, f.candidates.size)
        }

    @Test
    fun reentrantBusyCollectorCannotDispatchSecondWrite() =
        runTest {
            val f = RunCoordinatorFixture(this)
            f.start()
            val token = requireNotNull(f.coordinator.token())
            backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
                f.coordinator.state.collect { if (it.busy) f.coordinator.dispatch(token, ArenaRunAction.Timeout) }
            }
            f.coordinator.dispatch(token, ArenaRunAction.Answer(f.answer))
            runCurrent()
            assertEquals(1, f.candidates.size)
            assertFalse(f.coordinator.state.value.busy)
        }

    @Test
    fun backgroundAfterFeedbackClosesOnlyLockedAnswerAndWaitsPaused() =
        runTest {
            val f = RunCoordinatorFixture(this)
            f.start()
            f.coordinator.dispatch(requireNotNull(f.coordinator.token()), ArenaRunAction.Answer(f.answer))
            runCurrent()
            f.coordinator.dispatch(requireNotNull(f.coordinator.token()), ArenaRunAction.Background)
            runCurrent()
            val paused = f.current().checkpoint
            assertEquals(ArenaRunnerPhase.PAUSED, paused.phase)
            assertEquals(1, paused.indexInPool)
            assertEquals(1, paused.acceptedAnswers.size)
        }
}

private class RunCoordinatorFixture(private val scope: TestScope) {
    val stamp = ArenaSessionStamp("owner", 1)
    var session: ArenaSessionStamp? = stamp
    var now = 1500L
    var freshCalls = 0
    var readCalls = 0
    val candidates = mutableListOf<ArenaRunnerCheckpoint>()
    var beforeCas: suspend () -> Unit = {}
    var afterCas: suspend () -> Unit = {}
    val answer = UserAnswerDraft.SingleChoiceDraft(OptionId("A"))
    val header =
        ArenaTicketHeader(
            1, "a".repeat(64), "b".repeat(64), "owner", "request",
            ArenaEntryMode.SELECTED, Difficulty.EASY, "lesson", 1, ArenaTicketStatus.RESERVED, 1, 50, 100, "2026-09-08",
        )
    private val questions =
        (1..2).map { index ->
            val id = "q$index"
            ArenaTicketQuestion(
                id,
                1,
                index - 1,
                "en",
                "not-parsed-here",
                QuestionContent.SingleChoice(
                    id,
                    Difficulty.EASY,
                    "Question",
                    null,
                    listOf(QuestionContent.Option(OptionId("A"), "A"), QuestionContent.Option(OptionId("B"), "B")),
                    OptionId("A"),
                ),
            )
        }
    private val binding =
        ArenaRunnerCheckpointBinding(ArenaTicketSnapshot(header, "author", 1, null, questions), "c".repeat(64))
    private val initial =
        initialArenaRunnerCheckpoint(
            binding,
            ArenaInitialCheckpoint(
                SessionMode.LEARNING,
                91,
                1000,
                1000 + computeTimer(
                    questions.first().display,
                    Difficulty.EASY,
                    TimerCoefficients.Default,
                ).seconds * 1000L,
            ),
        )
    var observed: ArenaRunRead = ArenaRunRead.Prepared(header)
    private val repository =
        object : ArenaRunRepository {
            override suspend fun read(
                stamp: ArenaSessionStamp,
                ticketId: String,
            ): Result<ArenaRunRead> {
                assertEquals(this@RunCoordinatorFixture.stamp, stamp)
                assertEquals(header.ticketId, ticketId)
                readCalls++
                return Result.success(observed)
            }

            override suspend fun resumeOnly(
                stamp: ArenaSessionStamp,
                ticketId: String,
            ) = read(stamp, ticketId)

            override suspend fun freshClaim(
                stamp: ArenaSessionStamp,
                ticketId: String,
                sessionMode: SessionMode,
            ): Result<ArenaRunClaimOutcome> {
                freshCalls++
                assertEquals(this@RunCoordinatorFixture.stamp, stamp)
                observed = ArenaRunRead.Continuable(binding, initial, 0)
                return Result.success(ArenaRunClaimOutcome.Claimed(observed))
            }

            override suspend fun checkpointCAS(
                stamp: ArenaSessionStamp,
                ticketId: String,
                expectedRevision: Long,
                candidate: ArenaRunnerCheckpoint,
            ): Result<ArenaRunCasOutcome> {
                candidates += candidate
                beforeCas()
                val saved = observed as? ArenaRunRead.Continuable
                if (saved == null || saved.revision != expectedRevision) {
                    return Result.success(
                        ArenaRunCasOutcome.Conflict(observed),
                    )
                }
                validateArenaRunnerTransition(saved.checkpoint, candidate, saved.binding)
                observed =
                    if (candidate.phase == ArenaRunnerPhase.COMPLETING) {
                        ArenaRunRead.Completing(binding, candidate, expectedRevision + 1)
                    } else {
                        saved.copy(checkpoint = candidate, revision = expectedRevision + 1)
                    }
                afterCas()
                return Result.success(ArenaRunCasOutcome.Saved(observed))
            }

            override suspend fun finalize(
                stamp: ArenaSessionStamp,
                completing: ArenaRunRead.Completing,
            ): Nothing = error("Freeze is not part of reducer slice")
        }
    val coordinator =
        ArenaRunCoordinator(
            stamp,
            header.ticketId,
            repository,
            ArenaSessionProvider { session },
            scope.backgroundScope,
            object : Clock {
                override fun now(): Instant = Instant.fromEpochMilliseconds(now)
            },
        )

    fun current() = assertIs<ArenaRunRead.Continuable>(coordinator.state.value.observed)

    fun start() {
        coordinator.start(ArenaRunStart.Fresh(SessionMode.LEARNING))
        scope.runCurrent()
    }
}
