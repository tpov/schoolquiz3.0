@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.OptionUi
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.QuestionUiState
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.TemplatePart
import com.tpov.schoolquiz.shared.core.question_schema.BlankId
import com.tpov.schoolquiz.shared.core.question_schema.CandidateId
import com.tpov.schoolquiz.shared.core.question_schema.OptionId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft

@Composable
internal fun QuestionTypeContent(
    qState: QuestionUiState,
    currentDraft: UserAnswerDraft?,
    feedback: AnswerFeedback?,
    revealCorrect: Boolean,
    interaction: QuestionInteraction,
) {
    when (qState) {
        is QuestionUiState.Survey -> SurveyQuestion(qState, feedback as? AnswerFeedback.Survey, interaction)
        is QuestionUiState.SingleChoice ->
            SingleQuestion(qState, feedback as? AnswerFeedback.SingleChoice, revealCorrect, interaction)
        is QuestionUiState.MultipleChoice ->
            MultipleQuestion(
                qState,
                currentDraft,
                feedback as? AnswerFeedback.MultipleChoice,
                revealCorrect,
                interaction,
            )
        is QuestionUiState.Ordering ->
            OrderingQuestion(qState, currentDraft, feedback as? AnswerFeedback.Ordering, revealCorrect, interaction)
        is QuestionUiState.FillBlank ->
            FillQuestion(qState, currentDraft, feedback as? AnswerFeedback.FillBlank, revealCorrect, interaction)
    }
}

@Composable
private fun SurveyQuestion(
    question: QuestionUiState.Survey,
    feedback: AnswerFeedback.Survey?,
    input: QuestionInteraction,
) {
    SurveyContent(
        state = question,
        onOptionToggled = { optionId ->
            val selected = (input.readDraft() as? UserAnswerDraft.SurveyDraft)?.selected.orEmpty()
            val id = OptionId(optionId)
            val next =
                when {
                    !question.allowMultiple -> setOf(id)
                    id in selected -> selected - id
                    else -> selected + id
                }
            input.changeDraft(UserAnswerDraft.SurveyDraft(next))
        },
        onSubmit = {
            val answer = input.readDraft() as? UserAnswerDraft.SurveyDraft ?: UserAnswerDraft.SurveyDraft(emptySet())
            input.feedback(AnswerFeedback.Survey(answer, answer.selected.map { it.raw }.toSet()))
        },
        feedback = feedback,
    )
}

@Composable
private fun SingleQuestion(
    question: QuestionUiState.SingleChoice,
    feedback: AnswerFeedback.SingleChoice?,
    reveal: Boolean,
    input: QuestionInteraction,
) {
    val select: (String) -> Unit = { optionId ->
        val answer = UserAnswerDraft.SingleChoiceDraft(OptionId(optionId))
        input.selectAnswer(answer)
        input.feedback(AnswerFeedback.SingleChoice(answer, optionId, question.correctOptionId, reveal))
    }
    SingleChoiceContent(
        state = question,
        onOptionSelected = select,
        feedback = feedback,
        hintEnabled = input.hint.enabled,
        onHint = {
            val answer = question.hintDraft()
            val id = answer?.selected?.raw
            if (answer != null && id != null) input.hint.accept(answer) { select(id) }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun MultipleQuestion(
    question: QuestionUiState.MultipleChoice,
    draft: UserAnswerDraft?,
    feedback: AnswerFeedback.MultipleChoice?,
    reveal: Boolean,
    input: QuestionInteraction,
) {
    val selected =
        (draft as? UserAnswerDraft.MultipleChoiceDraft)?.selected?.map {
            it.raw
        }?.toSet() ?: question.selectedIds
    val submit: (Set<String>) -> Unit = { ids ->
        val answer = UserAnswerDraft.MultipleChoiceDraft(ids.map { OptionId(it) }.toSet())
        input.feedback(AnswerFeedback.MultipleChoice(answer, ids, question.correctIds, reveal))
    }
    MultipleChoiceContent(
        state = question.copy(selectedIds = selected),
        onOptionToggled = { id ->
            val next = if (id in selected) selected - id else selected + id
            input.changeDraft(UserAnswerDraft.MultipleChoiceDraft(next.map { OptionId(it) }.toSet()))
        },
        onSubmit = { submit(selected) },
        feedback = feedback,
        hintEnabled = input.hint.enabled,
        onHint = {
            val answer = question.hintDraft()
            if (answer != null) {
                input.hint.accept(answer) {
                    input.changeDraft(answer)
                    submit(answer.selected.map { it.raw }.toSet())
                }
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun OrderingQuestion(
    question: QuestionUiState.Ordering,
    draft: UserAnswerDraft?,
    feedback: AnswerFeedback.Ordering?,
    reveal: Boolean,
    input: QuestionInteraction,
) {
    val items = orderingItems(question, draft, feedback)
    val move: (Int, Int) -> Unit = { from, to ->
        val moved = items.toMutableList().also { it.add(to, it.removeAt(from)) }
        input.changeDraft(UserAnswerDraft.OrderingDraft(moved.map { OptionId(it.id) }))
    }
    val submit: (UserAnswerDraft.OrderingDraft) -> Unit = { answer ->
        input.feedback(AnswerFeedback.Ordering(answer, answer.order.map { it.raw }, question.correctOrderIds, reveal))
    }
    OrderingContent(
        state = question.copy(items = items),
        onMoveUp = { if (it > 0) move(it, it - 1) },
        onMoveDown = { if (it < items.lastIndex) move(it, it + 1) },
        onReorder = move,
        onSubmit = { submit(UserAnswerDraft.OrderingDraft(items.map { OptionId(it.id) })) },
        feedback = feedback,
        hintEnabled = input.hint.enabled,
        onHint = {
            val answer = question.hintDraft()
            if (answer != null) {
                input.hint.accept(answer) {
                    input.changeDraft(answer)
                    submit(answer)
                }
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

private fun orderingItems(
    question: QuestionUiState.Ordering,
    draft: UserAnswerDraft?,
    feedback: AnswerFeedback.Ordering?,
): List<OptionUi> {
    val draftIds = (draft as? UserAnswerDraft.OrderingDraft)?.order?.map { it.raw }
    val ids = feedback?.orderIds ?: draftIds?.takeIf { it.size == question.items.size }
    val byId = question.items.associateBy { it.id }
    return ids?.mapNotNull { byId[it] }?.takeIf { it.size == question.items.size } ?: question.items
}

@Composable
private fun FillQuestion(
    question: QuestionUiState.FillBlank,
    draft: UserAnswerDraft?,
    feedback: AnswerFeedback.FillBlank?,
    reveal: Boolean,
    input: QuestionInteraction,
) {
    val raw =
        (draft as? UserAnswerDraft.FillBlankDraft)?.filled
            ?.mapNotNull { (blank, candidate) -> candidate?.let { blank.raw to it.raw } }?.toMap().orEmpty()
    val blanks = question.templateParts.filterIsInstance<TemplatePart.Blank>()
    val byIndex = blanks.associateBy { it.index }
    val submit: (UserAnswerDraft.FillBlankDraft) -> Unit = { answer ->
        input.feedback(
            AnswerFeedback.FillBlank(
                answer,
                answer.filledByBlankIndex(blanks),
                question.correctCandidateIdsByBlankIndex,
                reveal,
            ),
        )
    }
    FillBlankContent(
        state = question.copy(filledValues = filledLabels(question, blanks, raw)),
        candidates = question.candidates,
        usedCandidateIds = raw.values.toSet(),
        onCandidateSelected = { index, candidate ->
            byIndex[index]?.let { input.changeDraft(fillDraft(raw + (it.blankId to candidate))) }
        },
        onBlankCleared = { index ->
            byIndex[index]?.let { input.changeDraft(fillDraft(raw - it.blankId)) }
        },
        onSubmit = { submit(fillDraft(raw)) },
        feedback = feedback,
        hintEnabled = input.hint.enabled,
        onHint = {
            val answer = question.hintDraft()
            if (answer != null) {
                input.hint.accept(answer) {
                    input.changeDraft(answer)
                    submit(answer)
                }
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

private fun fillDraft(raw: Map<String, String>) =
    UserAnswerDraft.FillBlankDraft(raw.mapKeys { BlankId(it.key) }.mapValues { CandidateId(it.value) })

private fun filledLabels(
    question: QuestionUiState.FillBlank,
    blanks: List<TemplatePart.Blank>,
    raw: Map<String, String>,
): Map<Int, String> {
    val candidates = question.candidates.associateBy { it.id }
    return raw.mapNotNull { (blankId, candidateId) ->
        val blank = blanks.firstOrNull { it.blankId == blankId } ?: return@mapNotNull null
        val text = candidates[candidateId]?.text ?: return@mapNotNull null
        blank.index to text
    }.toMap()
}
