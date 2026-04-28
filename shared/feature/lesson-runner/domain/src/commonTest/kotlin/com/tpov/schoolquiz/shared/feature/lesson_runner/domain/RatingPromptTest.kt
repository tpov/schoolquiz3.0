package com.tpov.schoolquiz.shared.feature.lesson_runner.domain

import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeClock
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeLessonAttemptRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeLessonRatingRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.CodeAnswer
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.CompleteAttemptUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Spec scenarios 48-51: Rating prompt visibility.
 * Tests via CompleteAttemptUseCase → RunnerState.Completed.ratingPrompt.
 *
 * Rule (Business Rule 16): ratingPrompt = allShownAnswersAre9 AND !hasSubmitted(userId, lessonId)
 */
class RatingPromptTest {

    private val clock = FakeClock()
    private val attemptRepo = FakeLessonAttemptRepository()
    private val ratingRepo = FakeLessonRatingRepository()
    private var idCounter = 0
    private val idProvider = { AttemptId("attempt-${idCounter++}") }

    private val useCase = CompleteAttemptUseCase(
        attemptRepository = attemptRepo,
        ratingRepository = ratingRepo,
        clock = clock,
        attemptIdProvider = idProvider,
    )

    private fun stateWithCodeAnswer(raw: String, userId: String = "user1", lessonId: String = "lesson1") =
        makeReadyState(
            userId = userId,
            lessonId = lessonId,
            codeAnswer = CodeAnswer(raw),
            eligibleSize = raw.length,
            playOrder = emptyList(),
            indexInPool = raw.length,
            mode = Difficulty.EASY,
        )

    // ── Test 48 ───────────────────────────────────────────────────────────────

    @Test
    fun `given allShownAnswersAre9 true and no submitted rating then ratingPrompt true`() = runTest {
        // Spec scenario #48
        val state = stateWithCodeAnswer("9999")
        val result = useCase(state) as RunnerState.Completed
        assertTrue(result.ratingPrompt, "Spec scenario #48: perfect attempt + no prior rating → prompt shown")
    }

    // ── Test 49 ───────────────────────────────────────────────────────────────

    @Test
    fun `given allShownAnswersAre9 true and already submitted rating then ratingPrompt false`() = runTest {
        // Spec scenario #49
        ratingRepo.setAlreadySubmitted("user1", LessonId("lesson1"))
        val state = stateWithCodeAnswer("9999")
        val result = useCase(state) as RunnerState.Completed
        assertFalse(result.ratingPrompt, "Spec scenario #49: already rated → prompt hidden")
    }

    // ── Test 49a ──────────────────────────────────────────────────────────────

    @Test
    fun `given CodeAnswer 9090 then allShownAnswersAre9 true and ratingPrompt true`() = runTest {
        // Spec scenario #49a: '9' and '0' only → valid perfect (shown answers are all 9)
        val state = stateWithCodeAnswer("9090")
        val result = useCase(state) as RunnerState.Completed
        assertTrue(result.ratingPrompt, "Spec scenario #49a: '9090' allShownAnswersAre9=true → prompt shown")
    }

    // ── Test 49b ──────────────────────────────────────────────────────────────

    @Test
    fun `given CodeAnswer 0000 then allShownAnswersAre9 false and ratingPrompt false`() = runTest {
        // Spec scenario #49b: no '9' at all → false
        val state = stateWithCodeAnswer("0000")
        val result = useCase(state) as RunnerState.Completed
        assertFalse(result.ratingPrompt, "Spec scenario #49b: all '0' → allShownAnswersAre9=false → prompt hidden")
    }

    // ── Test 49c ──────────────────────────────────────────────────────────────

    @Test
    fun `given CodeAnswer 9908 then allShownAnswersAre9 false and ratingPrompt false`() = runTest {
        // Spec scenario #49c: digit '8' breaks condition
        val state = stateWithCodeAnswer("9908")
        val result = useCase(state) as RunnerState.Completed
        assertFalse(result.ratingPrompt, "Spec scenario #49c: digit '8' → allShownAnswersAre9=false → prompt hidden")
    }

    // ── Test 50 ───────────────────────────────────────────────────────────────

    @Test
    fun `given attempt with one digit 5 not perfect then ratingPrompt false`() = runTest {
        // Spec scenario #50
        val state = stateWithCodeAnswer("5")
        val result = useCase(state) as RunnerState.Completed
        assertFalse(result.ratingPrompt, "Spec scenario #50: digit '5' → not perfect → prompt hidden")
    }

    // ── Test 51 ───────────────────────────────────────────────────────────────

    @Test
    fun `given attempt with all digits 1 then ratingPrompt false`() = runTest {
        // Spec scenario #51
        val state = stateWithCodeAnswer("1111")
        val result = useCase(state) as RunnerState.Completed
        assertFalse(result.ratingPrompt, "Spec scenario #51: all '1' → not perfect → prompt hidden")
    }
}
