package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui

import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.QuestionUiState
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.TemplatePart
import com.tpov.schoolquiz.shared.core.question_schema.BlankId
import com.tpov.schoolquiz.shared.core.question_schema.CandidateId
import com.tpov.schoolquiz.shared.core.question_schema.OptionId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft
import kotlin.random.Random

internal fun buildTimeoutFeedback(
    qState: QuestionUiState,
    currentDraft: UserAnswerDraft?,
    seed: Long,
    revealCorrect: Boolean,
): AnswerFeedback? {
    if (!qState.hasLocalAnswerKey && qState !is QuestionUiState.Survey) return null
    val random = Random(seed)
    return when (qState) {
        is QuestionUiState.Survey ->
            AnswerFeedback.Survey(
                answer =
                    currentDraft as? UserAnswerDraft.SurveyDraft
                        ?: UserAnswerDraft.SurveyDraft(emptySet()),
                selectedIds =
                    (currentDraft as? UserAnswerDraft.SurveyDraft)
                        ?.selected?.map { it.raw }?.toSet().orEmpty(),
            )
        is QuestionUiState.SingleChoice -> buildSingleChoiceTimeoutFeedback(qState, currentDraft, random, revealCorrect)
        is QuestionUiState.MultipleChoice ->
            buildMultipleChoiceTimeoutFeedback(
                qState,
                currentDraft,
                random,
                revealCorrect,
            )
        is QuestionUiState.Ordering -> buildOrderingTimeoutFeedback(qState, currentDraft, random, revealCorrect)
        is QuestionUiState.FillBlank -> buildFillBlankTimeoutFeedback(qState, currentDraft, random, revealCorrect)
    }
}

private fun buildSingleChoiceTimeoutFeedback(
    qState: QuestionUiState.SingleChoice,
    currentDraft: UserAnswerDraft?,
    random: Random,
    revealCorrect: Boolean,
): AnswerFeedback.SingleChoice? {
    val selectedId =
        (currentDraft as? UserAnswerDraft.SingleChoiceDraft)?.selected?.raw
            ?: qState.options.shuffled(random).firstOrNull()?.id
            ?: return null
    val draft = UserAnswerDraft.SingleChoiceDraft(OptionId(selectedId))
    return AnswerFeedback.SingleChoice(
        answer = draft,
        selectedId = selectedId,
        correctId = qState.correctOptionId,
        revealCorrect = revealCorrect,
    )
}

private fun buildMultipleChoiceTimeoutFeedback(
    qState: QuestionUiState.MultipleChoice,
    currentDraft: UserAnswerDraft?,
    random: Random,
    revealCorrect: Boolean,
): AnswerFeedback.MultipleChoice? {
    val validIds = qState.options.map { it.id }.toSet()
    if (validIds.isEmpty()) return null
    val selectedFromDraft =
        (currentDraft as? UserAnswerDraft.MultipleChoiceDraft)
            ?.selected
            ?.map { it.raw }
            ?.filter { it in validIds }
            ?.toSet()
            ?: emptySet()
    val targetSize = qState.correctIds.size.coerceAtLeast(1).coerceAtMost(validIds.size)
    val randomRemainder =
        qState.options
            .map { it.id }
            .filter { it !in selectedFromDraft }
            .shuffled(random)
            .take((targetSize - selectedFromDraft.size).coerceAtLeast(0))
    val selectedIds = selectedFromDraft + randomRemainder
    val draft = UserAnswerDraft.MultipleChoiceDraft(selectedIds.map { OptionId(it) }.toSet())
    return AnswerFeedback.MultipleChoice(
        answer = draft,
        selectedIds = selectedIds,
        correctIds = qState.correctIds,
        revealCorrect = revealCorrect,
    )
}

private fun buildOrderingTimeoutFeedback(
    qState: QuestionUiState.Ordering,
    currentDraft: UserAnswerDraft?,
    random: Random,
    revealCorrect: Boolean,
): AnswerFeedback.Ordering? {
    val validIds = qState.items.map { it.id }
    if (validIds.isEmpty()) return null
    val draftOrder =
        (currentDraft as? UserAnswerDraft.OrderingDraft)
            ?.order
            ?.map { it.raw }
            ?.filter { it in validIds }
    val orderIds =
        if (draftOrder.isNullOrEmpty()) {
            validIds.shuffled(random)
        } else {
            draftOrder + validIds.filter { it !in draftOrder }
        }
    val draft = UserAnswerDraft.OrderingDraft(orderIds.map { OptionId(it) })
    return AnswerFeedback.Ordering(
        answer = draft,
        orderIds = orderIds,
        correctOrderIds = qState.correctOrderIds,
        revealCorrect = revealCorrect,
    )
}

private fun buildFillBlankTimeoutFeedback(
    qState: QuestionUiState.FillBlank,
    currentDraft: UserAnswerDraft?,
    random: Random,
    revealCorrect: Boolean,
): AnswerFeedback.FillBlank? {
    val blankParts = qState.templateParts.filterIsInstance<TemplatePart.Blank>()
    val candidateIds = qState.candidates.map { it.id }
    if (blankParts.isEmpty() || candidateIds.isEmpty()) return null
    val validBlankIds = blankParts.map { it.blankId }.toSet()
    val validCandidateIds = candidateIds.toSet()
    val filledByBlankId =
        blankParts.associate { blank ->
            val draftCandidate =
                (currentDraft as? UserAnswerDraft.FillBlankDraft)
                    ?.filled
                    ?.get(BlankId(blank.blankId))
                    ?.raw
                    ?.takeIf { it in validCandidateIds }
            blank.blankId to (draftCandidate ?: candidateIds.shuffled(random).first())
        }.filterKeys { it in validBlankIds }
    val draft =
        UserAnswerDraft.FillBlankDraft(
            filledByBlankId
                .mapKeys { BlankId(it.key) }
                .mapValues { CandidateId(it.value) },
        )
    return AnswerFeedback.FillBlank(
        answer = draft,
        filledCandidateIdsByBlankIndex =
            blankParts.associate { blank ->
                blank.index to filledByBlankId.getValue(blank.blankId)
            },
        correctCandidateIdsByBlankIndex = qState.correctCandidateIdsByBlankIndex,
        revealCorrect = revealCorrect,
    )
}
