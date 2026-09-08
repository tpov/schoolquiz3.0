package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui

import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.OptionUi
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.QuestionUiState
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.TemplatePart
import com.tpov.schoolquiz.shared.core.question_schema.BlankId
import com.tpov.schoolquiz.shared.core.question_schema.CandidateId
import com.tpov.schoolquiz.shared.core.question_schema.OptionId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ArenaFeedbackMappingTest {
    @Test
    fun restoredFeedbackUsesLockedAnswerNotCurrentSelectionOrAnswerKey() {
        val question = single.copy(selectedOptionId = "A")
        val locked = UserAnswerDraft.SingleChoiceDraft(OptionId("B"))
        val feedback = savedArenaFeedback(question, locked, true) as AnswerFeedback.SingleChoice
        assertEquals("B", feedback.selectedId)
        assertEquals(locked, feedback.answer)
        assertEquals(1, feedback.revealDigit())
    }

    @Test
    fun hardAndExamNeverRevealDigitEvenWhenAnAnswerKeyAccidentallyExistsInUi() {
        val locked = UserAnswerDraft.SingleChoiceDraft(OptionId("A"))
        assertNull(savedArenaFeedback(single, locked, false).revealDigit())
        val redacted = single.copy(hasLocalAnswerKey = false)
        assertNull(savedArenaFeedback(redacted, locked, true).revealDigit())
    }

    @Test
    fun surveyRemainsUngradedAndKeepsEmptySelection() {
        val question = QuestionUiState.Survey("Q", false, null, options, setOf("A"), false)
        val locked = UserAnswerDraft.SurveyDraft(emptySet())
        val feedback = savedArenaFeedback(question, locked, true) as AnswerFeedback.Survey
        assertEquals(emptySet(), feedback.selectedIds)
        assertNull(feedback.revealDigit())
    }

    @Test
    fun multipleChoiceRestoresExactSetAndOrderingRestoresExactSequence() {
        val multiple = QuestionUiState.MultipleChoice("Q", false, null, options, setOf("A"), setOf("A"))
        val selected = UserAnswerDraft.MultipleChoiceDraft(setOf(OptionId("B")))
        val result = savedArenaFeedback(multiple, selected, true) as AnswerFeedback.MultipleChoice
        assertEquals(setOf("B"), result.selectedIds)
        val ordering = QuestionUiState.Ordering("Q", false, null, options, listOf("A", "B"))
        val ordered = UserAnswerDraft.OrderingDraft(listOf(OptionId("B"), OptionId("A")))
        assertEquals(
            listOf("B", "A"),
            (savedArenaFeedback(ordering, ordered, true) as AnswerFeedback.Ordering).orderIds,
        )
    }

    @Test
    fun fillBlankMapsActualLockedCandidatesNotCorrectCandidates() {
        val question =
            QuestionUiState.FillBlank(
                "Q",
                false,
                null,
                listOf(TemplatePart.Blank(0, "___", "blank")),
                mapOf(0 to "A"),
                options,
                mapOf(0 to "A"),
            )
        val locked = UserAnswerDraft.FillBlankDraft(mapOf(BlankId("blank") to CandidateId("B")))
        val feedback = savedArenaFeedback(question, locked, true) as AnswerFeedback.FillBlank
        assertEquals(mapOf(0 to "B"), feedback.filledCandidateIdsByBlankIndex)
        assertEquals(1, feedback.revealDigit())
    }

    @Test
    fun deferredHintSubmitsWithoutSpendingOrRevealingLocally() {
        val answer = UserAnswerDraft.SingleChoiceDraft(OptionId("A"))
        var submitted: UserAnswerDraft? = null
        var spent = false
        var revealed = false
        val hint =
            HintControl(true, {
                spent = true
                true
            }, { submitted = it })
        hint.accept(answer) { revealed = true }
        assertEquals(answer, submitted)
        assertFalse(spent)
        assertFalse(revealed)
    }

    @Test
    fun disabledHintDoesNothingAndOrdinaryHintStillRequiresSuccessfulSpend() {
        val answer = UserAnswerDraft.SingleChoiceDraft(OptionId("A"))
        var calls = 0
        HintControl(false, { error("disabled") }, { error("disabled") }).accept(answer) { error("disabled") }
        HintControl(true, { false }).accept(answer) { calls++ }
        assertEquals(0, calls)
        HintControl(true, { true }).accept(answer) { calls++ }
        assertEquals(1, calls)
    }

    private val options = listOf(OptionUi("A", "A"), OptionUi("B", "B"))
    private val single = QuestionUiState.SingleChoice("Q", false, null, options, null, "A")
}
