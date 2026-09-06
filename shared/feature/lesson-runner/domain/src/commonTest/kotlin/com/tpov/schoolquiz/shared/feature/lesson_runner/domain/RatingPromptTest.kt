package com.tpov.schoolquiz.shared.feature.lesson_runner.domain

import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.core.scoring.CodeAnswer
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeClock
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeLessonAttemptRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.CompleteAttemptUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Approved MVP rule replaces the old perfect-only/first-rating-ever rule.
 * Local completion requests a prompt at any score. Presentation still requires matching
 * SETTLED/completed=true server proof before it can display or submit the rating.
 */
class RatingPromptTest {
    private val attemptRepo = FakeLessonAttemptRepository()
    private val useCase = CompleteAttemptUseCase(attemptRepository = attemptRepo, clock = FakeClock())

    private fun state(raw: String) = makeReadyState(
        codeAnswer = CodeAnswer(raw), eligibleSize = raw.length,
        playOrder = emptyList(), indexInPool = raw.length, mode = Difficulty.EASY,
    )

    @Test
    fun allScoresCanRequestRatingAfterServerProofIncludingZero() = runTest {
        listOf("9999", "9090", "0000", "9908", "5", "1111").forEach { code ->
            val result = assertIs<RunnerState.Completed>(useCase(state(code)))
            assertTrue(result.ratingPrompt, "Local $code is not a rating eligibility gate")
        }
    }

    @Test
    fun anotherCompletionCanRequestAReplacementOfTheCurrentLessonRating() = runTest {
        val first = assertIs<RunnerState.Completed>(useCase(state("9").copy(attemptId = AttemptId("first"))))
        val next = assertIs<RunnerState.Completed>(useCase(state("1").copy(attemptId = AttemptId("next"))))
        assertTrue(first.ratingPrompt)
        assertTrue(next.ratingPrompt, "Earlier completion does not make a lesson unrateable forever")
    }

    @Test
    fun failedSaveDoesNotCreateCompletedRatingIntent() = runTest {
        attemptRepo.saveResult = Result.failure(IllegalStateException("save failed"))
        assertIs<RunnerState.SaveFailed>(useCase(state("9")))
    }
}
