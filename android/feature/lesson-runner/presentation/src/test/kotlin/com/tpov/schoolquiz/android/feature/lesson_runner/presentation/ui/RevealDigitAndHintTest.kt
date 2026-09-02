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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

    // Below: a question with no answer key has nothing to reveal, so there is no hint to sell.
    // Each of these used to hand back a draft, which is what let the runner take a charge for it.

    @Test
    fun `singleChoice without a correct id has no hint draft`() {
        val state =
            QuestionUiState.SingleChoice(
                questionText = "Q",
                hasImage = false,
                imageUrl = null,
                options = listOf(OptionUi("a", "A"), OptionUi("b", "B")),
                selectedOptionId = null,
                correctOptionId = null,
            )
        assertNull(buildHintDraft(state))
    }

    @Test
    fun `multipleChoice with an empty correct set has no hint draft`() {
        val state =
            QuestionUiState.MultipleChoice(
                questionText = "Q",
                hasImage = false,
                imageUrl = null,
                options = listOf(OptionUi("a", "A"), OptionUi("b", "B")),
                selectedIds = emptySet(),
                correctIds = emptySet(),
            )
        assertNull(buildHintDraft(state))
    }

    @Test
    fun `ordering with an empty correct order has no hint draft`() {
        val state =
            QuestionUiState.Ordering(
                questionText = "Q",
                hasImage = false,
                imageUrl = null,
                items = listOf(OptionUi("a", "A"), OptionUi("b", "B")),
                correctOrderIds = emptyList(),
            )
        assertNull(buildHintDraft(state))
    }

    @Test
    fun `fillBlank with no known blanks has no hint draft`() {
        assertNull(buildHintDraft(fillBlank(blankCount = 1, correct = emptyMap())))
    }

    // The key names blanks the template does not have, so nothing resolves and nothing fills.
    @Test
    fun `fillBlank whose correct indexes match no blank has no hint draft`() {
        assertNull(buildHintDraft(fillBlank(blankCount = 1, correct = mapOf(7 to "x"))))
    }

    /*
     * The live case. RunnerStateMapper keys the answer over every DECLARED blank while
     * parseTemplateParts emits a TemplatePart.Blank only for each `___` the text actually
     * carries, so a question declaring more blanks than it marks up exposes only some of them.
     * The domain still grades over all of them, so a draft covering only the reachable ones
     * would take the charge and come back marked wrong.
     */
    @Test
    fun `fillBlank whose template exposes fewer blanks than the key has no hint draft`() {
        val state = fillBlank(blankCount = 1, correct = mapOf(0 to "x", 1 to "y"))
        assertNull(
            buildHintDraft(state),
            "a hint that cannot reach every graded blank is not an answer, so it must not be sold",
        )
    }

    @Test
    fun `fillBlank whose correct candidate is not in the pool has no hint draft`() {
        assertNull(buildHintDraft(fillBlank(blankCount = 1, correct = mapOf(0 to "absent"))))
    }

    @Test
    fun `fillBlank hints when every template blank resolves`() {
        val state = fillBlank(blankCount = 2, correct = mapOf(0 to "x", 1 to "y"))
        assertEquals(
            UserAnswerDraft.FillBlankDraft(
                mapOf(BlankId("b0") to CandidateId("x"), BlankId("b1") to CandidateId("y")),
            ),
            buildHintDraft(state),
        )
    }

    // --- ids that are not on screen ---

    @Test
    fun `singleChoice whose correct id is not among the options has no hint draft`() {
        val state =
            QuestionUiState.SingleChoice(
                questionText = "Q",
                hasImage = false,
                imageUrl = null,
                options = listOf(OptionUi("a", "A"), OptionUi("b", "B")),
                selectedOptionId = null,
                correctOptionId = "ghost",
            )
        assertNull(buildHintDraft(state))
    }

    @Test
    fun `multipleChoice with a correct id that is not among the options has no hint draft`() {
        val state =
            QuestionUiState.MultipleChoice(
                questionText = "Q",
                hasImage = false,
                imageUrl = null,
                options = listOf(OptionUi("a", "A"), OptionUi("b", "B")),
                selectedIds = emptySet(),
                correctIds = setOf("a", "ghost"),
            )
        assertNull(buildHintDraft(state))
    }

    /*
     * OrderingContent keeps qState.items unless the order it is given is the same size, so a
     * short or foreign order would spend the charge and leave the list exactly where it was.
     */
    @Test
    fun `ordering whose correct order does not cover the items has no hint draft`() {
        assertNull(
            buildHintDraft(ordering(items = listOf("a", "b", "c"), correctOrder = listOf("a", "b"))),
            "a short order rearranges nothing, so it is not a hint",
        )
        assertNull(
            buildHintDraft(ordering(items = listOf("a", "b"), correctOrder = listOf("a", "ghost"))),
            "an order naming items that are not on screen rearranges nothing either",
        )
    }

    private fun ordering(
        items: List<String>,
        correctOrder: List<String>,
    ) = QuestionUiState.Ordering(
        questionText = "Q",
        hasImage = false,
        imageUrl = null,
        items = items.map { OptionUi(it, it.uppercase()) },
        correctOrderIds = correctOrder,
    )

    /** [blankCount] `___` markers in the template, blank ids `b0`, `b1`, …; pool is x, y, z. */
    private fun fillBlank(
        blankCount: Int,
        correct: Map<Int, String>,
    ): QuestionUiState.FillBlank {
        val parts = mutableListOf<TemplatePart>(TemplatePart.Text("A "))
        repeat(blankCount) { index ->
            parts += TemplatePart.Blank(index = index, placeholder = "___", blankId = "b$index")
            parts += TemplatePart.Text(" ")
        }
        return QuestionUiState.FillBlank(
            questionText = "A ___",
            hasImage = false,
            imageUrl = null,
            templateParts = parts,
            filledValues = emptyMap(),
            candidates = listOf(OptionUi("x", "X"), OptionUi("y", "Y"), OptionUi("z", "Z")),
            correctCandidateIdsByBlankIndex = correct,
        )
    }
}

/**
 * The wiring decision, on the JVM.
 *
 * `ciCheck` only *compiles* instrumented tests, so the screen's Compose tests pin nothing the
 * gate runs. [isHintAvailable] holds the whole enablement rule — difficulty, charges, verdict on
 * screen, and whether an answer exists — so restoring any part of the original bug fails here,
 * under `test`.
 */
class HintAvailabilityTest {

    private val hintable =
        listOf(
            "single choice" to
                QuestionUiState.SingleChoice(
                    questionText = "Q",
                    hasImage = false,
                    imageUrl = null,
                    options = listOf(OptionUi("a", "A"), OptionUi("b", "B")),
                    selectedOptionId = null,
                    correctOptionId = "b",
                ),
            "multiple choice" to
                QuestionUiState.MultipleChoice(
                    questionText = "Q",
                    hasImage = false,
                    imageUrl = null,
                    options = listOf(OptionUi("a", "A"), OptionUi("b", "B")),
                    selectedIds = emptySet(),
                    correctIds = setOf("a", "b"),
                ),
            "ordering" to
                QuestionUiState.Ordering(
                    questionText = "Q",
                    hasImage = false,
                    imageUrl = null,
                    items = listOf(OptionUi("b", "B"), OptionUi("a", "A")),
                    correctOrderIds = listOf("a", "b"),
                ),
            "fill blank" to
                QuestionUiState.FillBlank(
                    questionText = "A ___",
                    hasImage = false,
                    imageUrl = null,
                    templateParts =
                        listOf(
                            TemplatePart.Text("A "),
                            TemplatePart.Blank(index = 0, placeholder = "___", blankId = "b0"),
                        ),
                    filledValues = emptyMap(),
                    candidates = listOf(OptionUi("x", "X"), OptionUi("y", "Y")),
                    correctCandidateIdsByBlankIndex = mapOf(0 to "x"),
                ),
        )

    private val unhintable =
        listOf(
            "survey" to
                QuestionUiState.Survey(
                    questionText = "Q",
                    hasImage = false,
                    imageUrl = null,
                    options = listOf(OptionUi("a", "A")),
                    selectedIds = emptySet(),
                    allowMultiple = false,
                ),
            "single choice with no correct id" to
                QuestionUiState.SingleChoice(
                    questionText = "Q",
                    hasImage = false,
                    imageUrl = null,
                    options = listOf(OptionUi("a", "A"), OptionUi("b", "B")),
                    selectedOptionId = null,
                    correctOptionId = null,
                ),
            "multiple choice with an empty correct set" to
                QuestionUiState.MultipleChoice(
                    questionText = "Q",
                    hasImage = false,
                    imageUrl = null,
                    options = listOf(OptionUi("a", "A"), OptionUi("b", "B")),
                    selectedIds = emptySet(),
                    correctIds = emptySet(),
                ),
            "ordering with an empty correct order" to
                QuestionUiState.Ordering(
                    questionText = "Q",
                    hasImage = false,
                    imageUrl = null,
                    items = listOf(OptionUi("a", "A"), OptionUi("b", "B")),
                    correctOrderIds = emptyList(),
                ),
            // The production-reachable one: two graded blanks, one `___` in the text.
            "fill blank the template cannot fill" to
                QuestionUiState.FillBlank(
                    questionText = "A ___",
                    hasImage = false,
                    imageUrl = null,
                    templateParts =
                        listOf(
                            TemplatePart.Text("A "),
                            TemplatePart.Blank(index = 0, placeholder = "___", blankId = "b0"),
                        ),
                    filledValues = emptyMap(),
                    candidates = listOf(OptionUi("x", "X"), OptionUi("y", "Y")),
                    correctCandidateIdsByBlankIndex = mapOf(0 to "x", 1 to "y"),
                ),
        )

    @Test
    fun `an answerable easy question with a charge and no verdict offers the hint`() {
        hintable.forEach { (name, state) ->
            assertTrue(
                isHintAvailable(state, isHard = false, charges = 1, feedbackShown = false),
                "$name must offer the hint",
            )
        }
    }

    /**
     * The rule this class exists for: `spec-charges/SPEC.md:29,47` gives a standard charge two
     * sinks, and the hint one is an easy-question affordance. Hard answers gate stars, the hard
     * unlock and certification, so no charge may buy one — for any question type, however
     * answerable it is and however many charges are in hand.
     */
    @Test
    fun `a hard question never offers the hint, whatever else is true`() {
        hintable.forEach { (name, state) ->
            assertFalse(
                isHintAvailable(state, isHard = true, charges = 1, feedbackShown = false),
                "$name is hard, so no charge may buy its answer",
            )
            assertFalse(
                isHintAvailable(state, isHard = true, charges = 99, feedbackShown = false),
                "$name is hard, so a full wallet changes nothing",
            )
        }
    }

    @Test
    fun `a hard question with nothing to reveal is dead for both reasons`() {
        unhintable.forEach { (name, state) ->
            assertFalse(
                isHintAvailable(state, isHard = true, charges = 3, feedbackShown = false),
                "$name is hard and has no answer to play",
            )
        }
    }

    @Test
    fun `a question with nothing to reveal never offers the hint`() {
        unhintable.forEach { (name, state) ->
            assertFalse(
                isHintAvailable(state, isHard = false, charges = 3, feedbackShown = false),
                "$name has no answer to play, so the hint must stay dead even with charges in hand",
            )
        }
    }

    @Test
    fun `no charges means no hint`() {
        hintable.forEach { (name, state) ->
            assertFalse(
                isHintAvailable(state, isHard = false, charges = 0, feedbackShown = false),
                "$name, zero charges",
            )
            assertFalse(
                isHintAvailable(state, isHard = false, charges = null, feedbackShown = false),
                "$name, unknown charges",
            )
        }
    }

    @Test
    fun `a verdict already on screen means no hint`() {
        hintable.forEach { (name, state) ->
            assertFalse(
                isHintAvailable(state, isHard = false, charges = 3, feedbackShown = true),
                "$name, verdict shown",
            )
        }
    }

    /**
     * Easy is untouched by this rule: across the whole grid of the other terms, an easy question
     * answers exactly what it answered before difficulty was one of them. The expectations here
     * are written out rather than recomputed from the same expression the function uses, so a
     * change to that expression shows up as a failure instead of being copied into the check.
     */
    @Test
    fun `on easy the decision is unchanged across every other term`() {
        val chargeGrid = listOf(null to false, 0 to false, 1 to true, 3 to true)
        hintable.forEach { (name, state) ->
            chargeGrid.forEach { (charges, hasCharge) ->
                assertEquals(
                    hasCharge,
                    isHintAvailable(state, isHard = false, charges = charges, feedbackShown = false),
                    "$name, easy, charges=$charges, no verdict",
                )
                assertFalse(
                    isHintAvailable(state, isHard = false, charges = charges, feedbackShown = true),
                    "$name, easy, charges=$charges, verdict on screen",
                )
            }
        }
        unhintable.forEach { (name, state) ->
            chargeGrid.forEach { (charges, _) ->
                listOf(false, true).forEach { feedbackShown ->
                    assertFalse(
                        isHintAvailable(state, isHard = false, charges = charges, feedbackShown = feedbackShown),
                        "$name, easy, charges=$charges, feedbackShown=$feedbackShown",
                    )
                }
            }
        }
    }
}
