package com.tpov.schoolquiz.shared.feature.lesson_runner.domain

import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeClock
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeLessonAttemptRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeLessonRatingRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeAuthRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeLessonRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeQuestionContentParser
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeQuestionRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.submitAnswer
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
import com.tpov.schoolquiz.shared.core.scoring.CodeAnswer
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.InitFailureReason
import com.tpov.schoolquiz.shared.core.scoring.UserAnswer
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.AbortAttemptUseCase
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.StartLessonAttemptUseCase
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import com.tpov.schoolquiz.shared.core.scoring.Score

/**
 * Spec scenarios 78-82: State machine transitions.
 * Tests StartLessonAttemptUseCase → Ready, submitAnswer, AbortAttemptUseCase transitions.
 */
class StateMachineTest {

    private val clock = FakeClock()
    private val authRepo = FakeAuthRepository(uid = "user1")
    private val lessonRepo = FakeLessonRepository()
    private val questionRepo = FakeQuestionRepository()
    private val parser = FakeQuestionContentParser()
    private val attemptRepo = FakeLessonAttemptRepository()
    private val ratingRepo = FakeLessonRatingRepository()
    private var idCounter = 0
    private val attemptIdProvider = { AttemptId("attempt-${idCounter++}") }

    private val startUseCase get() = StartLessonAttemptUseCase(
        questionRepository = questionRepo,
        lessonRepository = lessonRepo,
        parser = parser,
        authRepository = authRepo,
        clock = clock,
        randomSeedProvider = { 12345L },
    )

    private val abortUseCase = AbortAttemptUseCase(
        attemptRepository = attemptRepo,
        clock = clock,
        attemptIdProvider = attemptIdProvider,
    )

    private fun setupValidLesson(id: String = "lesson1") {
        lessonRepo.addLesson(makeLesson(id = id))
        val q = makeQuestion(id = "q1", lessonId = id, order = 0)
        parser.addResponse(q.payload, singleChoiceContent(difficulty = Difficulty.EASY))
        questionRepo.addQuestion(q)
    }

    // ── Test 78: Loading → Ready ──────────────────────────────────────────────

    @Test
    fun `given RunnerState Loading when StartLessonAttemptUseCase succeeds then returns Ready`() = runTest {
        // Spec scenario #78
        setupValidLesson()
        val result = startUseCase(LessonId("lesson1"), Difficulty.EASY)
        assertIs<RunnerState.Ready>(result, "Spec scenario #78: success → Ready")
    }

    // ── Test 79: Loading → InitFailed ─────────────────────────────────────────

    @Test
    fun `given RunnerState Loading when eligible empty then InitFailed EmptyPool`() = runTest {
        // Spec scenario #79: HARD mode but lesson only has EASY questions → EmptyPool
        setupValidLesson()
        val result = startUseCase(LessonId("lesson1"), Difficulty.HARD)
        assertIs<RunnerState.InitFailed>(result, "Spec scenario #79: empty → InitFailed")
        assertEquals(InitFailureReason.EmptyPool, result.reason)
    }

    // ── Test 80: last question → sentinel ────────────────────────────────────

    @Test
    fun `given Ready with indexInPool at last question when submitAnswer then indexInPool equals playOrder size sentinel`() {
        // Spec scenario #80: last question answered → indexInPool == playOrder.size
        val questions = listOf(makeRunnerQuestion(id = "q1", order = 0, codeAnswerIndex = 0))
        val state = makeReadyState(
            playOrder = questions,
            eligibleSize = 1,
            indexInPool = 0,
            codeAnswer = CodeAnswer("0"),
        )
        val answer = UserAnswer.SingleChoiceAnswer(selected = optId("A"))
        val result = submitAnswer(state, answer, nowMs = 1_000_000L)

        assertEquals(
            state.playOrder.size,
            result.indexInPool,
            "Spec scenario #80: after last answer → indexInPool == playOrder.size (sentinel)",
        )
    }

    // ── Test 81: mid-session → indexInPool+1 ──────────────────────────────────

    @Test
    fun `given Ready mid-index when submitAnswer then indexInPool incremented by 1`() {
        // Spec scenario #81
        val questions = listOf(
            makeRunnerQuestion(id = "q1", order = 0, codeAnswerIndex = 0),
            makeRunnerQuestion(id = "q2", order = 1, codeAnswerIndex = 1),
            makeRunnerQuestion(id = "q3", order = 2, codeAnswerIndex = 2),
        )
        val state = makeReadyState(
            playOrder = questions,
            eligibleSize = 3,
            indexInPool = 1,
            codeAnswer = CodeAnswer("900"),
        )
        val answer = UserAnswer.SingleChoiceAnswer(selected = optId("A"))
        val result = submitAnswer(state, answer, nowMs = 1_000_000L)

        assertEquals(2, result.indexInPool, "Spec scenario #81: indexInPool goes from 1 → 2")
    }

    // ── Test 82: Ready → Aborted/SaveFailed ───────────────────────────────────

    @Test
    fun `given Ready when AbortAttemptUseCase then returns Aborted`() = runTest {
        // Spec scenario #82: Abort → Aborted (happy path)
        val state = makeReadyState(
            playOrder = listOf(makeRunnerQuestion(codeAnswerIndex = 0)),
            eligibleSize = 1,
            indexInPool = 1,
            codeAnswer = CodeAnswer("9"),
        )
        val result = abortUseCase(state)
        assertIs<RunnerState.Aborted>(result, "Spec scenario #82: Abort → Aborted")
    }

    @Test
    fun `given Ready when AbortAttemptUseCase save fails then returns SaveFailed`() = runTest {
        // Spec scenario #82 failure path
        attemptRepo.saveResult = Result.failure(RuntimeException("IO error"))
        val state = makeReadyState(
            playOrder = listOf(makeRunnerQuestion(codeAnswerIndex = 0)),
            eligibleSize = 1,
            indexInPool = 1,
            codeAnswer = CodeAnswer("9"),
        )
        val result = abortUseCase(state)
        assertIs<RunnerState.SaveFailed>(result, "Spec scenario #82: save failure → SaveFailed")
    }

    @Test
    fun `given submitAnswer records score digit in codeAnswer at correct position`() {
        // Verify that submitAnswer writes the score to the correct codeAnswerIndex position
        val questions = listOf(
            makeRunnerQuestion(id = "q1", order = 0, codeAnswerIndex = 2),
        )
        val state = makeReadyState(
            playOrder = questions,
            eligibleSize = 5,
            indexInPool = 0,
            codeAnswer = CodeAnswer("00000"),
        )
        val answer = UserAnswer.SingleChoiceAnswer(selected = optId("A"))
        val result = submitAnswer(state, answer, nowMs = 1_000_000L)
        assertEquals('9', result.codeAnswer.raw[2], "Score written at codeAnswerIndex=2")
        assertEquals('0', result.codeAnswer.raw[0], "Other positions unchanged")
    }
}
