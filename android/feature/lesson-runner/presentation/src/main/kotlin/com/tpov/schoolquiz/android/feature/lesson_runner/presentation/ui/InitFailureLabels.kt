package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui

import androidx.annotation.StringRes
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.R
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.RunnerUiState

/**
 * Which message a failed init shows.
 *
 * Lifted out of `InitFailedContent` so that the choice is a pure function of the reason and can be
 * asserted in `src/test`, the way [com.tpov.schoolquiz.android.feature.app_shell.presentation] does
 * for its tab and drawer labels. Inside the composable this mapping was pinned by nothing the gate
 * runs: the `when` branches are adjacent and near-identical, the component test reads only the
 * enum, and the Compose tests that would have caught a swap are instrumented — `ciCheck` compiles
 * those but never runs them. A redacted lesson could therefore have gone back to reading "questions
 * are invalid" with the whole gate green, which is the single confusion this slice exists to
 * remove.
 */
val RunnerUiState.InitFailureReason.messageRes: Int
    @StringRes get() =
        when (this) {
            RunnerUiState.InitFailureReason.AuthRequired ->
                R.string.runner_error_auth_required
            RunnerUiState.InitFailureReason.LessonNotFound ->
                R.string.runner_error_lesson_not_found
            RunnerUiState.InitFailureReason.EmptyPool ->
                R.string.runner_error_empty_pool
            RunnerUiState.InitFailureReason.NoValidQuestions ->
                R.string.runner_error_no_valid_questions
            RunnerUiState.InitFailureReason.RedactedNotSupported ->
                R.string.runner_error_redacted_questions
        }
