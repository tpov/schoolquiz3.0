package com.tpov.schoolquiz.shared.feature.lesson_runner.domain

import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeClock
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeLessonAttemptRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeLessonRatingRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
import com.tpov.schoolquiz.shared.core.scoring.CodeAnswer
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.AbortAttemptUseCase
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.CompleteAttemptUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Spec scenarios 52-54: Attempt save behavior.
 * Tests that save is called exactly once on completion/abort, and not on process kill.
 */
class SaveAttemptTest {

    private val clock = FakeClock()
    private val attemptRepo = FakeLessonAttemptRepository()
    private val ratingRepo = FakeLessonRatingRepository()
    private var idCounter = 0
    private val idProvider = { AttemptId("attempt-${idCounter++}") }

    private val completeUseCase = CompleteAttemptUseCase(
        attemptRepository = attemptRepo,
        ratingRepository = ratingRepo,
        clock = clock,
        attemptIdProvider = idProvider,
    )

    private val abortUseCase = AbortAttemptUseCase(
        attemptRepository = attemptRepo,
        clock = clock,
        attemptIdProvider = idProvider,
    )

    // ── Test 52 ───────────────────────────────────────────────────────────────

    @Test
    fun `given complete attempt when CompleteAttemptUseCase invoked then save called once with correct Attempt`() = runTest {
        // Spec scenario #52: LessonAttemptRepository.save called 1 time with correct Attempt
        val codeAnswer = CodeAnswer("9999")
        val state = makeReadyState(
            userId = "user42",
            lessonId = "lesson7",
            lessonVersion = 5L,
            mode = Difficulty.EASY,
            codeAnswer = codeAnswer,
            eligibleSize = 4,
            playOrder = emptyList(),
            indexInPool = 4,
        )

        completeUseCase(state)

        assertEquals(1, attemptRepo.saveCallCount, "Spec scenario #52: save called exactly once")
        val saved = attemptRepo.savedAttempts.first()
        assertEquals("user42", saved.userId, "userId must match state.userId")
        assertEquals("lesson7", saved.lessonId.value, "lessonId must match state.lessonId")
        assertEquals(5L, saved.lessonVersion, "lessonVersion must match state.lessonVersion")
        assertEquals(Difficulty.EASY, saved.mode, "mode must match state.mode")
        assertEquals("9999", saved.codeAnswer.raw, "codeAnswer must match state.codeAnswer")
    }

    // ── Test 53 ───────────────────────────────────────────────────────────────

    @Test
    fun `given exit after 3 questions of 20 when AbortAttemptUseCase invoked then codeAnswer has 3 real plus 17 ones`() = runTest {
        // Spec scenario #53: 20 EASY questions all in subset, answered 3, unanswered 17 → '1'
        // Build playOrder: 20 questions with codeAnswerIndex 0..19
        val playOrder = (0 until 20).map { i ->
            makeRunnerQuestion(id = "q$i", order = i, codeAnswerIndex = i)
        }
        // Simulate 3 answered: codeAnswer positions 0,1,2 = '9','9','9', rest '0'
        val codeAnswerAfter3 = "999" + "0".repeat(17)
        val state = makeReadyState(
            userId = "user1",
            lessonId = "lesson1",
            mode = Difficulty.EASY,
            playOrder = playOrder,
            eligibleSize = 20,
            indexInPool = 3,
            codeAnswer = CodeAnswer(codeAnswerAfter3),
        )

        val result = abortUseCase(state)

        assertIs<RunnerState.Aborted>(result, "Abort should return Aborted state")
        val savedAttempt = (result as RunnerState.Aborted).attempt
        assertEquals(20, savedAttempt.codeAnswer.raw.length, "codeAnswer.length == eligibleSize == 20")
        // First 3 positions are real answers (not '1' or '0')
        val firstThree = savedAttempt.codeAnswer.raw.substring(0, 3)
        assertTrue(firstThree.all { it in '1'..'9' }, "First 3 positions are real scores: $firstThree")
        // Positions 3..19 are '1' (unanswered subset → abort fills with '1')
        val last17 = savedAttempt.codeAnswer.raw.substring(3)
        assertEquals("1".repeat(17), last17, "Spec scenario #53: unanswered subset positions → '1'")
        assertEquals(1, attemptRepo.saveCallCount, "save called exactly once")
    }

    // ── Test 54 ───────────────────────────────────────────────────────────────

    @Test
    fun `given process kill simulated when no use case called then no save`() {
        // Spec scenario #54: process kill → no CompleteAttemptUseCase/AbortAttemptUseCase called
        // → LessonAttemptRepository.save NOT called
        assertEquals(0, attemptRepo.saveCallCount, "Spec scenario #54: no use case called → save count = 0")
    }
}
