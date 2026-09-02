package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case

import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.chargeClaimsOrEmpty
import com.tpov.schoolquiz.shared.core.scoring.computePercentScore
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.SaveError
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.toServedQuestions
import com.tpov.schoolquiz.shared.core.scoring.allShownAnswersAre9
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonAttemptRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonRatingRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock

/**
 * Finalizes a completed attempt and persists it to Room.
 *
 * userId taken from [state.userId] — no repeated auth read.
 * ratingPrompt = codeAnswer.allShownAnswersAre9 AND !hasSubmittedRating.
 *
 * Returns [RunnerState.Completed] or [RunnerState.SaveFailed] on Room IO error.
 */
class CompleteAttemptUseCase(
    private val attemptRepository: LessonAttemptRepository,
    private val ratingRepository: LessonRatingRepository,
    private val clock: Clock,
    private val attemptIdProvider: () -> AttemptId,
) {
    suspend operator fun invoke(state: RunnerState.Ready): RunnerState {
        val nowMs = clock.now().toEpochMilliseconds()
        val percentScore = computePercentScore(state.codeAnswer)

        val attempt = Attempt(
            id = attemptIdProvider(),
            userId = state.userId,
            lessonId = state.lessonId,
            lessonVersion = state.lessonVersion,
            mode = state.mode,
            completedAt = nowMs,
            codeAnswer = state.codeAnswer,
            percentScore = percentScore,
        )

        // Every question dealt into the play order is served. The server drops the list today; once
        // the wiring step reads it, each digit will be placed from here rather than from the client's
        // word on which positions were shown.
        val saveResult = attemptRepository.save(attempt, state.answers, state.playOrder.toServedQuestions(), state.chargeClaimsOrEmpty())
        if (saveResult.isFailure) {
            val error = saveResult.exceptionOrNull()
                ?.let { SaveError.IoFailure(it) }
                ?: SaveError.UnknownError(Exception("Unknown save error"))
            return RunnerState.SaveFailed(attempt = attempt, error = error)
        }

        val ratingPrompt = state.codeAnswer.allShownAnswersAre9 &&
            !ratingRepository.hasSubmitted(state.userId, state.lessonId).first()

        return RunnerState.Completed(attempt = attempt, ratingPrompt = ratingPrompt)
    }
}
