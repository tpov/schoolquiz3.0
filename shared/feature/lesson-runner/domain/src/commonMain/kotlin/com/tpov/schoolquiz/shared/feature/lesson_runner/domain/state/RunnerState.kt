package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state

import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AnsweredQuestion
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
import com.tpov.schoolquiz.shared.core.scoring.ChargeClaimMask
import com.tpov.schoolquiz.shared.core.scoring.CodeAnswer
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.InitFailureReason
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.RunnerQuestion
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.SaveError
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.SessionMode
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft

/**
 * State machine for the lesson runner lifecycle.
 *
 * Transitions (happy path): Loading → Ready → Completed
 * Error paths: Loading → InitFailed; Ready → Aborted | SaveFailed
 */
sealed interface RunnerState {

    /** Initial state while StartLessonAttemptUseCase is loading questions. */
    data object Loading : RunnerState

    /** Terminal error: attempt was not started. */
    data class InitFailed(val reason: InitFailureReason) : RunnerState

    /**
     * Active gameplay state. Immutable snapshot of questions fixed at attempt start.
     *
     * [indexInPool] sentinel value [playOrder.size] means last answer was submitted;
     * component must call CompleteAttemptUseCase immediately.
     *
     * [eligibleSize] = length of codeAnswer = total questions of [mode] difficulty
     * in this lesson (after archive filter + parse filter), NOT subset size.
     */
    data class Ready(
        val userId: String,
        val lessonId: LessonId,
        val lessonVersion: Long,
        val mode: Difficulty,
        val playOrder: List<RunnerQuestion.Valid>,
        val eligibleSize: Int,
        val indexInPool: Int,
        val codeAnswer: CodeAnswer,
        val deadlineMs: Long,
        val seed: Long,
        val currentDraftAnswer: UserAnswerDraft?,
        val isPaused: Boolean,
        /** Practice or exam; independent of [mode], which is the question difficulty. */
        val sessionMode: SessionMode = SessionMode.LEARNING,
        /** When the current question appeared; used to measure how long the answer took. */
        val questionStartedAtMs: Long = 0L,
        /** Answers recorded so far, persisted together with the attempt when the lesson ends. */
        val answers: List<AnsweredQuestion> = emptyList(),
        /**
         * Заявки на заряды по позициям [codeAnswer] — `null`, пока ни одной не было.
         *
         * Заявка, а не списание (CAP-3): игрок нажал «потратить заряд», а списывает сервер при
         * отправке результата. Хранится рядом с цифрами, а не внутри них: `codeAnswer` остаётся
         * цифрами, и попытка без заявок ведёт себя ровно как сегодня.
         */
        val chargeClaims: ChargeClaimMask? = null,
    ) : RunnerState

    /** Terminal success: attempt saved, optional rating prompt shown. */
    data class Completed(
        val attempt: Attempt,
        val ratingPrompt: Boolean,
    ) : RunnerState

    /** Terminal: user exited mid-session; attempt saved with partial codeAnswer. */
    data class Aborted(val attempt: Attempt) : RunnerState

    /** Terminal: attempt constructed but Room write failed. */
    data class SaveFailed(
        val attempt: Attempt,
        val error: SaveError,
    ) : RunnerState
}
