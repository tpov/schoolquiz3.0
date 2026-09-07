package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case

import com.tpov.schoolquiz.shared.core.scoring.computePercentScore
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.chargeClaimsOrEmpty
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptCompletionKind
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.SaveError
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.toServedQuestions
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonAttemptRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
import kotlinx.datetime.Clock

/**
 * Finalizes a completed attempt and persists it to Room.
 *
 * userId taken from [state.userId] — no repeated auth read.
 * Every newly completed attempt may request a latest lesson rating, regardless of local score.
 * This flag is only intent: UI still requires SETTLED/completed=true proof for the active owner.
 *
 * Returns [RunnerState.Completed] or [RunnerState.SaveFailed] on Room IO error.
 */
class CompleteAttemptUseCase(
    private val attemptRepository: LessonAttemptRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(state: RunnerState.Ready): RunnerState {
        val nowMs = clock.now().toEpochMilliseconds()
        val localCode =
            state.codeAnswer
                ?: return saveUnscoredTerminal(state, AttemptCompletionKind.COMPLETED, nowMs, attemptRepository)
        val percentScore = computePercentScore(localCode)

        val attempt =
            Attempt(
                id = state.attemptId,
                userId = state.userId,
                lessonId = state.lessonId,
                lessonVersion = state.lessonVersion,
                mode = state.mode,
                completedAt = nowMs,
                codeAnswer = localCode,
                percentScore = percentScore,
            )

        // Every question dealt into the play order is served. The server reads this list: it places
        // every digit of a hard attempt it scores itself, and on an attempt this device scored it is
        // checked against the digits — a list that disagrees with them is stored, marked and paid
        // nothing (`servedVerified` in `functions/attempt-intake.js`). A list built wrong here is a
        // list that stops paying the player, so it is not a diagnostic field.
        val saveResult =
            attemptRepository.save(
                attempt,
                state.answers,
                state.playOrder.toServedQuestions(),
                state.chargeClaimsOrEmpty(),
                AttemptCompletionKind.COMPLETED,
            )
        return if (saveResult.isFailure) {
            val error =
                saveResult.exceptionOrNull()
                    ?.let { SaveError.IoFailure(it) }
                    ?: SaveError.UnknownError(Exception("Unknown save error"))
            RunnerState.SaveFailed(attempt = attempt, error = error)
        } else {
            RunnerState.Completed(attempt = attempt, ratingPrompt = true)
        }
    }
}
