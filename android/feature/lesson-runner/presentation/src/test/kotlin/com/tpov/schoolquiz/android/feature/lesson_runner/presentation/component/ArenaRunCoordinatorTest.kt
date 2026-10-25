@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component

import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.core.question_schema.OptionId
import com.tpov.schoolquiz.shared.core.question_schema.QuestionContent
import com.tpov.schoolquiz.shared.core.question_schema.RedactedQuestionContent
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.ArenaOnlineO2Adapter
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.ArenaRunTransitions
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
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.QuestionDelivery
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.QuestionDeliveryVerdict
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.QuestionWindowView
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.SessionMode
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.TimerCoefficients
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaRunCasOutcome
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaRunClaimOutcome
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaRunDecision
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaRunFinalizeOutcome
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

    // ── Онлайн-O2 боковой канал: итог возвращается писателю чистым кандидатом (ретро A1) ──────

    @Test
    fun externalServerResumeIsCasWrittenThroughTheSameSerializedWriter() =
        runTest {
            val f = RunCoordinatorFixture(this)
            f.start()
            f.coordinator.dispatch(requireNotNull(f.coordinator.token()), ArenaRunAction.Answer(f.answer))
            runCurrent()
            f.coordinator.dispatch(requireNotNull(f.coordinator.token()), ArenaRunAction.Background)
            runCurrent()
            val token = requireNotNull(f.coordinator.token())
            assertEquals(ArenaRunnerPhase.PAUSED, token.phase)

            // Чистый кандидат из свежего состояния — как строит его адаптер из серверной выдачи.
            f.candidates.clear()
            f.coordinator.applyExternal(token) { current, nowMs ->
                ArenaRunTransitions.continueRun(current, nowMs + 40_000).copy(
                    deadlineMs = nowMs + 24_000,
                )
            }
            runCurrent()
            val resumed = assertIs<ArenaRunRead.Continuable>(f.coordinator.state.value.observed)
            assertEquals(ArenaRunnerPhase.ANSWERING, resumed.checkpoint.phase)
            assertEquals(f.now + 24_000, resumed.checkpoint.deadlineMs, "the server remainder anchors the local projection")
            assertEquals(1, f.candidates.size, "the side-channel result went through the same CAS writer")
            assertNull(f.coordinator.state.value.notice)
        }

    @Test
    fun adapterDivergenceInsideExternalApplyIsReloadRequiredNeverASilentFallback() =
        runTest {
            val f = RunCoordinatorFixture(this)
            f.start()
            val token = requireNotNull(f.coordinator.token())
            // Чекпоинт в ANSWERING: адаптерный require (resumeDelivered требует паузу) падает —
            // расхождение становится RELOAD_REQUIRED, локальный таймер не подменяет сервер.
            f.coordinator.applyExternal(token) { current, _ ->
                ArenaOnlineO2Adapter.resumeDelivered(current, 0, view = view(), verdict = delivered(), localNowMs = f.now)
            }
            runCurrent()
            assertEquals(ArenaRunNotice.RELOAD_REQUIRED, f.coordinator.state.value.notice)
            assertFalse(f.coordinator.state.value.busy)
            assertEquals(0, f.candidates.size, "a diverged side-channel result never reaches the CAS")
        }

    @Test
    fun pureSideDivergenceBeforeTheWriterStopsWithReloadRequired() =
        runTest {
            val f = RunCoordinatorFixture(this)
            f.start()
            f.coordinator.diverge()
            assertEquals(ArenaRunNotice.RELOAD_REQUIRED, f.coordinator.state.value.notice)
            assertFalse(f.coordinator.state.value.busy)
            assertNull(f.coordinator.token(), "a diverged run is not interactive")
        }

    /** Минимальное честное окно под адаптерные проверки: identity совпадает, конфигурация валидна. */
    private fun view() =
        QuestionWindowView(
            windowId = "win_test",
            attemptId = "b".repeat(64),
            completedQuestionIndex = 0,
            nextQuestionIndex = 1,
            state = ArenaOnlineO2Adapter.WINDOW_STATE_OPEN,
            windowSeconds = 60,
            allocatedSeconds = 24,
            openedAtMs = 1_500_000,
            expiresAtMs = 1_560_000,
        )

    private fun delivered() =
        QuestionDeliveryVerdict.Delivered(
            windowId = "win_test",
            delivery = QuestionDelivery(1, "q2", 1_550_000, 1_574_000),
            remainderMs = 24_000,
            serverNowMs = 1_550_000,
        )
}

internal class RunCoordinatorFixture(
    private val scope: TestScope,
    /** Сложность попытки; HARD нужен онлайн-O2-тестам бокового канала Continue-из-паузы. */
    val difficulty: Difficulty = Difficulty.EASY,
) {
    val stamp = ArenaSessionStamp("owner", 1)
    var session: ArenaSessionStamp? = stamp
    var now = 1500L
    var freshCalls = 0
    var readCalls = 0
    val candidates = mutableListOf<ArenaRunnerCheckpoint>()
    var beforeCas: suspend () -> Unit = {}
    var afterCas: suspend () -> Unit = {}
    var freezeCalls = 0
    var afterFreeze: suspend () -> Unit = {}
    val answer = UserAnswerDraft.SingleChoiceDraft(OptionId("A"))
    val header =
        ArenaTicketHeader(
            1, "a".repeat(64), "b".repeat(64), "owner", "request",
            ArenaEntryMode.SELECTED, difficulty, "lesson", 1, ArenaTicketStatus.RESERVED, 1, 50, 100, "2026-09-08",
        )
    private val questions =
        (1..2).map { index ->
            val id = "q$index"
            // HARD-банк приезжает на клиент отредактированным (ключи не покидают сервер):
            // биндинг требует RedactedQuestionContent/Survey, иначе честный чекпоинт не собрать.
            val display =
                if (difficulty == Difficulty.HARD) {
                    RedactedQuestionContent.SingleChoice(
                        id = id,
                        difficulty = difficulty.name,
                        text = "Question",
                        imageUrl = null,
                        options =
                            listOf(
                                RedactedQuestionContent.Row("A", "A"),
                                RedactedQuestionContent.Row("B", "B"),
                            ),
                    )
                } else {
                    QuestionContent.SingleChoice(
                        id,
                        difficulty,
                        "Question",
                        null,
                        listOf(QuestionContent.Option(OptionId("A"), "A"), QuestionContent.Option(OptionId("B"), "B")),
                        OptionId("A"),
                    )
                }
            ArenaTicketQuestion(
                id,
                1,
                index - 1,
                "en",
                "not-parsed-here",
                display,
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
                    difficulty,
                    TimerCoefficients.Default,
                ).seconds * 1000L,
            ),
        )
    var observed: ArenaRunRead = ArenaRunRead.Prepared(header)
    val repository =
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
            ): Result<ArenaRunFinalizeOutcome> {
                assertEquals(this@RunCoordinatorFixture.stamp, stamp)
                assertEquals(observed, completing)
                freezeCalls++
                observed = ArenaRunRead.DecisionPending(header, ArenaRunDecision.SETTLE)
                afterFreeze()
                return Result.success(ArenaRunFinalizeOutcome.FrozenLocally(header.ticketId, header.attemptId))
            }
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
