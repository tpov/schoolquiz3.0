package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui

import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.QuestionUiState
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.TemplatePart
import com.tpov.schoolquiz.shared.core.question_schema.BlankId
import com.tpov.schoolquiz.shared.core.question_schema.CandidateId
import com.tpov.schoolquiz.shared.core.question_schema.OptionId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft

/**
 * The answer the hint plays for the user: the fully correct draft for [qState].
 * Null for surveys — an opinion has no right version to reveal.
 */
internal fun buildHintDraft(qState: QuestionUiState): UserAnswerDraft? =
    when (qState) {
        is QuestionUiState.Survey -> null
        is QuestionUiState.SingleChoice ->
            qState.correctOptionId?.let { UserAnswerDraft.SingleChoiceDraft(OptionId(it)) }
        is QuestionUiState.MultipleChoice ->
            UserAnswerDraft.MultipleChoiceDraft(qState.correctIds.map { OptionId(it) }.toSet())
        is QuestionUiState.Ordering ->
            UserAnswerDraft.OrderingDraft(qState.correctOrderIds.map { OptionId(it) })
        is QuestionUiState.FillBlank -> buildHintFillBlankDraft(qState)
    }

/** Correct blanks keyed by blank id; blanks without a known index are skipped defensively. */
private fun buildHintFillBlankDraft(qState: QuestionUiState.FillBlank): UserAnswerDraft {
    val blankIdByIndex =
        qState.templateParts
            .filterIsInstance<TemplatePart.Blank>()
            .associateBy({ it.index }) { it.blankId }
    val filled =
        qState.correctCandidateIdsByBlankIndex.mapNotNull { (index, candidateId) ->
            val blankId = blankIdByIndex[index] ?: return@mapNotNull null
            BlankId(blankId) to CandidateId(candidateId)
        }.toMap()
    return UserAnswerDraft.FillBlankDraft(filled)
}
