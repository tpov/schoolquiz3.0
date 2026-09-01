package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui

import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.R
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.RunnerUiState
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The reason-to-message mapping, which nothing the gate runs used to check.
 *
 * While it lived inside `InitFailedContent` the only tests that could have caught a swapped branch
 * were the instrumented CT cases, and `ciCheck` compiles those without running them. The branches
 * are adjacent and near-identical, so a redacted lesson reading "questions are invalid" was a
 * one-line mistake away with the whole gate still green.
 */
class InitFailureLabelsTest {

    @Test
    fun `each reason names its own message`() {
        assertEquals(R.string.runner_error_auth_required, RunnerUiState.InitFailureReason.AuthRequired.messageRes)
        assertEquals(R.string.runner_error_lesson_not_found, RunnerUiState.InitFailureReason.LessonNotFound.messageRes)
        assertEquals(R.string.runner_error_empty_pool, RunnerUiState.InitFailureReason.EmptyPool.messageRes)
        assertEquals(
            R.string.runner_error_no_valid_questions,
            RunnerUiState.InitFailureReason.NoValidQuestions.messageRes,
        )
        assertEquals(
            R.string.runner_error_redacted_questions,
            RunnerUiState.InitFailureReason.RedactedNotSupported.messageRes,
        )
    }

    @Test
    fun `no two reasons share a message`() {
        // The specific confusion this slice exists to remove: a redacted lesson and a corrupt one
        // must not print the same sentence. Asserted over the whole enum so that any future pair
        // collapsing onto one string fails here too.
        val byMessage = RunnerUiState.InitFailureReason.entries.groupBy { it.messageRes }

        assertEquals(
            RunnerUiState.InitFailureReason.entries.size,
            byMessage.size,
            "reasons sharing a message: ${byMessage.filterValues { it.size > 1 }.values}",
        )
    }
}
