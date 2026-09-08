package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui

import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.QuestionUiState
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.TemplatePart
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft

/** Only call with the locked draft of a strictly read FEEDBACK checkpoint, never a proposed answer. */
internal fun savedArenaFeedback(
    question: QuestionUiState,
    locked: UserAnswerDraft,
    revealCorrect: Boolean,
): AnswerFeedback {
    val reveal = revealCorrect && question.hasLocalAnswerKey
    return when (question) {
        is QuestionUiState.SingleChoice -> {
            val answer = locked as UserAnswerDraft.SingleChoiceDraft
            AnswerFeedback.SingleChoice(
                answer,
                // Empty is not an option ID: a saved omission selects no row.
                answer.selected?.raw.orEmpty(),
                question.correctOptionId,
                reveal,
            )
        }
        is QuestionUiState.MultipleChoice -> {
            val answer = locked as UserAnswerDraft.MultipleChoiceDraft
            AnswerFeedback.MultipleChoice(answer, answer.selected.map { it.raw }.toSet(), question.correctIds, reveal)
        }
        is QuestionUiState.Ordering -> {
            val answer = locked as UserAnswerDraft.OrderingDraft
            AnswerFeedback.Ordering(answer, answer.order.map { it.raw }, question.correctOrderIds, reveal)
        }
        is QuestionUiState.FillBlank -> {
            val answer = locked as UserAnswerDraft.FillBlankDraft
            AnswerFeedback.FillBlank(
                answer,
                answer.filledByBlankIndex(question.templateParts.filterIsInstance<TemplatePart.Blank>()),
                question.correctCandidateIdsByBlankIndex,
                reveal,
            )
        }
        is QuestionUiState.Survey -> {
            val answer = locked as UserAnswerDraft.SurveyDraft
            AnswerFeedback.Survey(answer, answer.selected.map { it.raw }.toSet())
        }
    }
}
