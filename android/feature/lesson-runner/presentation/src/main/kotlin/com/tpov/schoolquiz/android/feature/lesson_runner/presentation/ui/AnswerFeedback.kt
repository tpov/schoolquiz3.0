package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui

import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft

enum class AnswerFeedbackTone {
    Neutral,
    Correct,
    Wrong,
}

sealed interface AnswerFeedback {
    val answer: UserAnswerDraft

    data class SingleChoice(
        override val answer: UserAnswerDraft.SingleChoiceDraft,
        val selectedId: String,
        val correctId: String?,
    ) : AnswerFeedback

    data class MultipleChoice(
        override val answer: UserAnswerDraft.MultipleChoiceDraft,
        val selectedIds: Set<String>,
        val correctIds: Set<String>,
    ) : AnswerFeedback

    data class Ordering(
        override val answer: UserAnswerDraft.OrderingDraft,
        val orderIds: List<String>,
        val correctOrderIds: List<String>,
    ) : AnswerFeedback

    data class FillBlank(
        override val answer: UserAnswerDraft.FillBlankDraft,
        val filledCandidateIdsByBlankIndex: Map<Int, String>,
        val correctCandidateIdsByBlankIndex: Map<Int, String>,
    ) : AnswerFeedback
}
