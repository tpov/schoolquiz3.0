package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state

import com.tpov.schoolquiz.shared.core.leaderboard.TopParticipant
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PercentScore
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft

sealed interface RunnerUiState {
    data object Loading : RunnerUiState

    data class InitFailed(
        val reason: InitFailureReason,
    ) : RunnerUiState

    data class Question(
        val questionUiState: QuestionUiState,
        val indexInPool: Int,
        val totalInPool: Int,
        val deadlineMs: Long,
        val isPaused: Boolean,
        val isHard: Boolean,
        /** Whether the right answer may be shown after answering (practice on easy only). */
        val revealCorrect: Boolean = true,
        val showExitConfirmDialog: Boolean,
        val currentDraft: UserAnswerDraft? = null,
    ) : RunnerUiState

    data class Result(
        val percentScore: PercentScore,
        val mode: Difficulty,
        val completedAt: Long,
        val hardUnlocked: Boolean,
        val bestStarsRawTenths: Int,
        /** Stars earned in THIS attempt (spec §148), distinct from best stars across all attempts. */
        val currentAttemptStarsRawTenths: Int,
        val lessonAverageRating: Float?,
        val lessonRatingCount: Int,
        val top3: List<TopParticipant>,
        val userAttemptCount: Int,
        val userAveragePercentScore: Int,
        val userBestPercentScore: Int = percentScore.raw,
        val showRatingPrompt: Boolean,
        val saveWarning: Boolean,
        val ratingSubmissionState: RatingSubmissionState = RatingSubmissionState.Idle,
    ) : RunnerUiState

    sealed interface RatingSubmissionState {
        data object Idle : RatingSubmissionState

        data object InProgress : RatingSubmissionState

        data object Failed : RatingSubmissionState

        data object Done : RatingSubmissionState
    }

    enum class InitFailureReason {
        AuthRequired,
        LessonNotFound,
        EmptyPool,
        NoValidQuestions,
    }
}
