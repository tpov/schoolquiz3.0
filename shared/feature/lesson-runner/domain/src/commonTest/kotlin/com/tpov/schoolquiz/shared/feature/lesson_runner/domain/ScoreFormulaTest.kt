package com.tpov.schoolquiz.shared.feature.lesson_runner.domain

import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.core.question_schema.OptionId
import kotlin.test.Test
import kotlin.test.assertEquals
import com.tpov.schoolquiz.shared.core.scoring.Score
import com.tpov.schoolquiz.shared.core.scoring.UserAnswer
import com.tpov.schoolquiz.shared.core.scoring.evaluateAnswer

/**
 * Spec scenarios 1-12: Score formula for all question types.
 * Tests `evaluateAnswer(content, answer): Score`.
 */
class ScoreFormulaTest {

    // ── SingleChoice (tests 1-2) ──────────────────────────────────────────────

    @Test
    fun `given SingleChoice correctOptionId A when selected A then digit 9`() {
        val content = singleChoiceContent(correctId = "A", optionIds = listOf("A", "B", "C", "D"))
        val answer = UserAnswer.SingleChoiceAnswer(selected = optId("A"))
        val score = evaluateAnswer(content, answer)
        assertEquals(9, score.raw, "Spec scenario #1: correct single choice → digit 9")
    }

    @Test
    fun `given SingleChoice correctOptionId A when selected B then digit 1`() {
        val content = singleChoiceContent(correctId = "A", optionIds = listOf("A", "B", "C", "D"))
        val answer = UserAnswer.SingleChoiceAnswer(selected = optId("B"))
        val score = evaluateAnswer(content, answer)
        assertEquals(1, score.raw, "Spec scenario #2: wrong single choice → digit 1")
    }

    // ── MultipleChoice (tests 3-6) ────────────────────────────────────────────

    @Test
    fun `given MultipleChoice correctIds ABC 5 options when selected AB then digit 6`() {
        // Spec scenario #3: 2 correct, 0 wrong, 1 missed → correct_share = 2/3 → round(0.667×8)+1 = 6
        val content = multipleChoiceContent(
            correctIds = setOf("A", "B", "C"),
            allOptionIds = listOf("A", "B", "C", "D", "E"),
        )
        val answer = UserAnswer.MultipleChoiceAnswer(selected = setOf(optId("A"), optId("B")))
        val score = evaluateAnswer(content, answer)
        assertEquals(6, score.raw, "Spec scenario #3: Jaccard 2/(2+0+1) = 0.667 → digit 6")
    }

    @Test
    fun `given MultipleChoice when selected all correct then digit 9`() {
        // Spec scenario #4
        val content = multipleChoiceContent(
            correctIds = setOf("A", "B", "C"),
            allOptionIds = listOf("A", "B", "C", "D", "E"),
        )
        val answer = UserAnswer.MultipleChoiceAnswer(selected = setOf(optId("A"), optId("B"), optId("C")))
        val score = evaluateAnswer(content, answer)
        assertEquals(9, score.raw, "Spec scenario #4: all correct → digit 9")
    }

    @Test
    fun `given MultipleChoice correctIds AB when selected CD then digit 1`() {
        // Spec scenario #5: 0 correct, 2 wrong, 2 missed → correct_share = 0/4 = 0 → digit 1
        val content = multipleChoiceContent(
            correctIds = setOf("A", "B"),
            allOptionIds = listOf("A", "B", "C", "D"),
        )
        val answer = UserAnswer.MultipleChoiceAnswer(selected = setOf(optId("C"), optId("D")))
        val score = evaluateAnswer(content, answer)
        assertEquals(1, score.raw, "Spec scenario #5: 0 correct → digit 1")
    }

    @Test
    fun `given MultipleChoice when selected empty then digit 1`() {
        // Spec scenario #6: selected={} → correct_share = 0 → digit 1
        val content = multipleChoiceContent(
            correctIds = setOf("A", "B", "C"),
            allOptionIds = listOf("A", "B", "C", "D", "E"),
        )
        val answer = UserAnswer.MultipleChoiceAnswer(selected = emptySet())
        val score = evaluateAnswer(content, answer)
        assertEquals(1, score.raw, "Spec scenario #6: empty selection → digit 1")
    }

    // ── Ordering (tests 7-9) ──────────────────────────────────────────────────

    @Test
    fun `given Ordering ABCD when userOrder ABCD then digit 9`() {
        // Spec scenario #7
        val content = orderingContent(itemIds = listOf("A", "B", "C", "D"))
        val answer = UserAnswer.OrderingAnswer(order = listOf("A", "B", "C", "D").map { optId(it) })
        val score = evaluateAnswer(content, answer)
        assertEquals(9, score.raw, "Spec scenario #7: all positions match → digit 9")
    }

    @Test
    fun `given Ordering ABCD when userOrder ACBD then digit 5`() {
        // Spec scenario #8: matched = A at [0], D at [3] = 2/4 = 0.5 → round(0.5×8)+1 = 5
        val content = orderingContent(itemIds = listOf("A", "B", "C", "D"))
        val answer = UserAnswer.OrderingAnswer(order = listOf("A", "C", "B", "D").map { optId(it) })
        val score = evaluateAnswer(content, answer)
        assertEquals(5, score.raw, "Spec scenario #8: 2/4 match → digit 5")
    }

    @Test
    fun `given Ordering ABCD when userOrder DCBA then digit 1`() {
        // Spec scenario #9: 0 matches → correct_share = 0 → digit 1
        val content = orderingContent(itemIds = listOf("A", "B", "C", "D"))
        val answer = UserAnswer.OrderingAnswer(order = listOf("D", "C", "B", "A").map { optId(it) })
        val score = evaluateAnswer(content, answer)
        assertEquals(1, score.raw, "Spec scenario #9: 0 position matches → digit 1")
    }

    // ── FillBlank (tests 10-12) ───────────────────────────────────────────────

    @Test
    fun `given FillBlank 3 blanks when all 3 correct then digit 9`() {
        // Spec scenario #10
        val content = fillBlankContent(
            blankCorrectPairs = listOf("b1" to "c1", "b2" to "c2", "b3" to "c3"),
            candidateIds = listOf("c1", "c2", "c3", "c4", "c5"),
        )
        val answer = UserAnswer.FillBlankAnswer(
            filled = mapOf(blankId("b1") to candId("c1"), blankId("b2") to candId("c2"), blankId("b3") to candId("c3")),
        )
        val score = evaluateAnswer(content, answer)
        assertEquals(9, score.raw, "Spec scenario #10: all blanks correct → digit 9")
    }

    @Test
    fun `given FillBlank 3 blanks when 1 correct then digit 4`() {
        // Spec scenario #11: correct_share = 1/3 ≈ 0.333 → round(0.333×8)+1 = round(2.667)+1 = 4
        val content = fillBlankContent(
            blankCorrectPairs = listOf("b1" to "c1", "b2" to "c2", "b3" to "c3"),
            candidateIds = listOf("c1", "c2", "c3", "c4", "c5"),
        )
        val answer = UserAnswer.FillBlankAnswer(
            filled = mapOf(blankId("b1") to candId("c1"), blankId("b2") to candId("c4"), blankId("b3") to candId("c5")),
        )
        val score = evaluateAnswer(content, answer)
        assertEquals(4, score.raw, "Spec scenario #11: 1/3 blanks correct → digit 4")
    }

    @Test
    fun `given FillBlank 1 blank when wrong then digit 1`() {
        // Spec scenario #12
        val content = fillBlankContent(
            blankCorrectPairs = listOf("b1" to "c1"),
            candidateIds = listOf("c1", "c2", "c3", "c4", "c5"),
        )
        val answer = UserAnswer.FillBlankAnswer(
            filled = mapOf(blankId("b1") to candId("c2")),
        )
        val score = evaluateAnswer(content, answer)
        assertEquals(1, score.raw, "Spec scenario #12: wrong blank → digit 1")
    }

    // ── Null / timeout guard ──────────────────────────────────────────────────

    @Test
    fun `given SingleChoice when selected null then digit 1`() {
        val content = singleChoiceContent(correctId = "A")
        val answer = UserAnswer.SingleChoiceAnswer(selected = null)
        val score = evaluateAnswer(content, answer)
        assertEquals(1, score.raw, "timeout/null selection → digit 1")
    }
}
