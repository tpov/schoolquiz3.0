package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui

import com.tpov.schoolquiz.shared.core.question_schema.BlankId
import com.tpov.schoolquiz.shared.core.question_schema.CandidateId
import com.tpov.schoolquiz.shared.core.question_schema.OptionId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.OptionUi
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.QuestionUiState
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state.TemplatePart
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private fun opt(id: String) = OptionId(id)

class RevealDigitTest {

    @Test
    fun `singleChoice correct gives 9 wrong gives 1`() {
        val feedback = feedbackSingle(selected = "a", correct = "a")
        assertEquals(9, feedback.revealDigit())
        assertEquals(1, feedbackSingle(selected = "b", correct = "a").revealDigit())
    }

    // F7: multiple choice is graded by the Jaccard share (hits over the picked∪correct union),
    // not all-or-nothing.
    @Test
    fun `multipleChoice fullMatch gives 9`() {
        val feedback = feedbackMulti(selected = setOf("a", "b"), correct = setOf("a", "b"))
        assertEquals(9, feedback.revealDigit())
    }

    @Test
    fun `multipleChoice oneHitOneWrongOfTwoCorrect gives middle digit`() {
        // hit=1, union={a,b,c}=3 → round(1/3 × 8) + 1 = 3 + 1 = 4
        val feedback = feedbackMulti(selected = setOf("a", "c"), correct = setOf("a", "b"))
        assertEquals(4, feedback.revealDigit())
    }

    @Test
    fun `multipleChoice nothingPicked gives 1`() {
        val feedback = feedbackMulti(selected = emptySet(), correct = setOf("a", "b"))
        assertEquals(1, feedback.revealDigit())
    }

    @Test
    fun `ordering matchedPositions share`() {
        // correct order [a, b, c]; submitted [a, c, b] → 1 of 3 → round(2.67) + 1 = 4
        val feedback =
            AnswerFeedback.Ordering(
                answer = UserAnswerDraft.OrderingDraft(listOf(opt("a"), opt("c"), opt("b"))),
                orderIds = listOf("a", "c", "b"),
                correctOrderIds = listOf("a", "b", "c"),
            )
        assertEquals(4, feedback.revealDigit())
    }

    @Test
    fun `ordering perfect gives 9`() {
        val feedback =
            AnswerFeedback.Ordering(
                answer = UserAnswerDraft.OrderingDraft(listOf(opt("a"), opt("b"), opt("c"))),
                orderIds = listOf("a", "b", "c"),
                correctOrderIds = listOf("a", "b", "c"),
            )
        assertEquals(9, feedback.revealDigit())
    }

    @Test
    fun `fillBlank correctBlanks share`() {
        val feedback =
            AnswerFeedback.FillBlank(
                answer = UserAnswerDraft.FillBlankDraft(emptyMap()),
                filledCandidateIdsByBlankIndex = mapOf(0 to "x", 1 to "y"),
                correctCandidateIdsByBlankIndex = mapOf(0 to "x", 1 to "z"),
            )
        // 1 of 2 → 5
        assertEquals(5, feedback.revealDigit())
    }

    @Test
    fun `noReveal and survey give null`() {
        assertNull(feedbackSingle(selected = "a", revealCorrect = false).revealDigit())
        assertNull(feedbackSurvey().revealDigit())
    }

    private fun feedbackSingle(
        selected: String,
        correct: String? = null,
        revealCorrect: Boolean = true,
    ) = AnswerFeedback.SingleChoice(
        answer = UserAnswerDraft.SingleChoiceDraft(opt(selected)),
        selectedId = selected,
        correctId = correct ?: selected,
        revealCorrect = revealCorrect,
    )

    private fun feedbackMulti(
        selected: Set<String>,
        correct: Set<String>,
    ) = AnswerFeedback.MultipleChoice(
        answer = UserAnswerDraft.MultipleChoiceDraft(selected.map { opt(it) }.toSet()),
        selectedIds = selected,
        correctIds = correct,
    )

    private fun feedbackSurvey() =
        AnswerFeedback.Survey(
            answer = UserAnswerDraft.SurveyDraft(setOf(opt("a"))),
            selectedIds = setOf("a"),
        )
}

class BuildHintDraftTest {

    @Test
    fun `singleChoice hint picks the correct option`() {
        val state =
            QuestionUiState.SingleChoice(
                questionText = "Q",
                hasImage = false,
                imageUrl = null,
                options = listOf(OptionUi("a", "A"), OptionUi("b", "B")),
                selectedOptionId = null,
                correctOptionId = "b",
            )
        assertEquals(UserAnswerDraft.SingleChoiceDraft(opt("b")), buildHintDraft(state))
    }

    @Test
    fun `multipleChoice hint picks every correct option`() {
        val state =
            QuestionUiState.MultipleChoice(
                questionText = "Q",
                hasImage = false,
                imageUrl = null,
                options = listOf(OptionUi("a", "A"), OptionUi("b", "B"), OptionUi("c", "C")),
                selectedIds = emptySet(),
                correctIds = setOf("a", "c"),
            )
        assertEquals(
            UserAnswerDraft.MultipleChoiceDraft(setOf(opt("a"), opt("c"))),
            buildHintDraft(state),
        )
    }

    @Test
    fun `ordering hint produces the content order`() {
        val state =
            QuestionUiState.Ordering(
                questionText = "Q",
                hasImage = false,
                imageUrl = null,
                items = listOf(OptionUi("c", "C"), OptionUi("a", "A"), OptionUi("b", "B")),
                correctOrderIds = listOf("a", "b", "c"),
            )
        assertEquals(
            UserAnswerDraft.OrderingDraft(listOf(opt("a"), opt("b"), opt("c"))),
            buildHintDraft(state),
        )
    }

    @Test
    fun `fillBlank hint fills every blank correctly by id`() {
        val state =
            QuestionUiState.FillBlank(
                questionText = "A ___ B ___",
                hasImage = false,
                imageUrl = null,
                templateParts =
                    listOf(
                        TemplatePart.Text("A "),
                        TemplatePart.Blank(index = 0, placeholder = "___", blankId = "b0"),
                        TemplatePart.Text(" B "),
                        TemplatePart.Blank(index = 1, placeholder = "___", blankId = "b1"),
                    ),
                filledValues = emptyMap(),
                candidates = listOf(OptionUi("x", "X"), OptionUi("y", "Y")),
                correctCandidateIdsByBlankIndex = mapOf(0 to "y", 1 to "x"),
            )
        assertEquals(
            UserAnswerDraft.FillBlankDraft(
                mapOf(BlankId("b0") to CandidateId("y"), BlankId("b1") to CandidateId("x")),
            ),
            buildHintDraft(state),
        )
    }

    @Test
    fun `survey has no hint draft`() {
        val state =
            QuestionUiState.Survey(
                questionText = "Q",
                hasImage = false,
                imageUrl = null,
                options = listOf(OptionUi("a", "A")),
                selectedIds = emptySet(),
                allowMultiple = false,
            )
        assertNull(buildHintDraft(state))
    }
}
