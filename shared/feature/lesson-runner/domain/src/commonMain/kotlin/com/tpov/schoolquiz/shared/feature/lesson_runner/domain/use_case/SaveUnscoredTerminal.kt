package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case

import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.chargeClaimsOrEmpty
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptCompletionKind
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptIdentity
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.SaveError
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UnscoredTerminalIntent
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.toServedQuestions
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonAttemptRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
import kotlinx.coroutines.CancellationException

internal suspend fun saveUnscoredTerminal(
    state: RunnerState.Ready,
    kind: AttemptCompletionKind,
    nowMs: Long,
    repository: LessonAttemptRepository,
): RunnerState {
    check(state.codeAnswer == null && state.answers.isEmpty())
    val intent =
        UnscoredTerminalIntent(
            identity = AttemptIdentity(state.attemptId, state.userId, state.lessonId, state.lessonVersion, state.mode),
            completionKind = kind,
            endedAtMs = nowMs,
            answers = state.rawAnswers.toList(),
            served = state.playOrder.toServedQuestions(),
            claims = state.chargeClaimsOrEmpty(),
        )
    val result = repository.saveUnscored(intent)
    result.exceptionOrNull()?.let { error ->
        if (error is CancellationException) throw error
        return RunnerState.PendingSaveFailed(intent, SaveError.IoFailure(error))
    }
    return when (kind) {
        AttemptCompletionKind.COMPLETED -> RunnerState.PendingCompletion(intent)
        AttemptCompletionKind.ABORTED -> RunnerState.PendingAbort(intent)
    }
}
