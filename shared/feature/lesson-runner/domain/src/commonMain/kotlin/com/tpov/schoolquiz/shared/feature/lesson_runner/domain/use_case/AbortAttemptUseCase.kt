package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case

import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.buildCodeAnswerOnAbort
import com.tpov.schoolquiz.shared.core.scoring.computePercentScore
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.SaveError
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.toServedQuestions
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonAttemptRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
import kotlinx.datetime.Clock

/**
 * Aborts an in-progress attempt and persists a partial record.
 *
 * userId from [state.userId] — no auth read.
 * Unanswered subset positions → '1'; out-of-subset positions remain '0'.
 *
 * Returns [RunnerState.Aborted] or [RunnerState.SaveFailed] on Room IO error.
 */
class AbortAttemptUseCase(
    private val attemptRepository: LessonAttemptRepository,
    private val clock: Clock,
    private val attemptIdProvider: () -> AttemptId,
) {
    suspend operator fun invoke(state: RunnerState.Ready): RunnerState {
        val nowMs = clock.now().toEpochMilliseconds()
        val finalCodeAnswer = buildCodeAnswerOnAbort(state)
        val percentScore = computePercentScore(finalCodeAnswer)

        val attempt = Attempt(
            id = attemptIdProvider(),
            userId = state.userId,
            lessonId = state.lessonId,
            lessonVersion = state.lessonVersion,
            mode = state.mode,
            completedAt = nowMs,
            codeAnswer = finalCodeAnswer,
            percentScore = percentScore,
        )

        // The whole play order is served, not just the reached part: buildCodeAnswerOnAbort gave the
        // unreached questions '1' above, so they count as shown, and the served list has to say the
        // same — sending only the reached ones would shrink the denominator and inflate the percent.
        val saveResult = attemptRepository.save(attempt, state.answers, state.playOrder.toServedQuestions())
        if (saveResult.isFailure) {
            val error = saveResult.exceptionOrNull()
                ?.let { SaveError.IoFailure(it) }
                ?: SaveError.UnknownError(Exception("Unknown save error"))
            return RunnerState.SaveFailed(attempt = attempt, error = error)
        }

        return RunnerState.Aborted(attempt = attempt)
    }
}
