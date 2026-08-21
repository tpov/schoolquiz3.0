package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui

import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft

enum class AnswerFeedbackTone {
    Neutral,
    Correct,
    Wrong,
}

sealed interface AnswerFeedback {
    val answer: UserAnswerDraft

    /**
     * Whether the right answer may be shown. False during an exam and on hard questions, where
     * the answer is the assessment — see SessionMode.revealsCorrectAnswer.
     */
    val revealCorrect: Boolean

    data class SingleChoice(
        override val answer: UserAnswerDraft.SingleChoiceDraft,
        val selectedId: String,
        val correctId: String?,
        override val revealCorrect: Boolean = true,
    ) : AnswerFeedback

    data class MultipleChoice(
        override val answer: UserAnswerDraft.MultipleChoiceDraft,
        val selectedIds: Set<String>,
        val correctIds: Set<String>,
        override val revealCorrect: Boolean = true,
    ) : AnswerFeedback

    data class Ordering(
        override val answer: UserAnswerDraft.OrderingDraft,
        val orderIds: List<String>,
        val correctOrderIds: List<String>,
        override val revealCorrect: Boolean = true,
    ) : AnswerFeedback

    /** Survey answers are never graded, so there is nothing to compare against. */
    data class Survey(
        override val answer: UserAnswerDraft.SurveyDraft,
        val selectedIds: Set<String>,
        override val revealCorrect: Boolean = false,
    ) : AnswerFeedback

    data class FillBlank(
        override val answer: UserAnswerDraft.FillBlankDraft,
        val filledCandidateIdsByBlankIndex: Map<Int, String>,
        val correctCandidateIdsByBlankIndex: Map<Int, String>,
        override val revealCorrect: Boolean = true,
    ) : AnswerFeedback
}
