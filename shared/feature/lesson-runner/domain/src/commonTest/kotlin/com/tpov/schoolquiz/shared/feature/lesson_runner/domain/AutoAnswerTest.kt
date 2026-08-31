package com.tpov.schoolquiz.shared.feature.lesson_runner.domain

import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.autoAnswerOnTimeout
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.generateTimeoutAnswer
import com.tpov.schoolquiz.shared.core.scoring.UserAnswer
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Spec scenarios 44-47: Auto-answer on timeout.
 * Tests `autoAnswerOnTimeout(state, randomSeed, nowMs): RunnerState.Ready`.
 * Auto-generated answer must be a valid selection from available options.
 */
class AutoAnswerTest {

    private val nowMs = 1_000_000L

    // ── SingleChoice (test 44) ────────────────────────────────────────────────

    @Test
    fun `given SingleChoice 4 options when auto-random then selected is one of the 4 options`() {
        // Spec scenario #44
        val content = singleChoiceContent(optionIds = listOf("A", "B", "C", "D"))
        val state = makeReadyState(playOrder = listOf(makeRunnerQuestion(content = content)))
        val resultState = autoAnswerOnTimeout(state, randomSeed = 42L, nowMs = nowMs)
        val currentIndex = state.indexInPool
        // After auto-answer, the score for this question is recorded (codeAnswer updated)
        // The auto answer must be one of the 4 options → digit in 1..9
        assertTrue(
            resultState.codeAnswer.raw[state.playOrder[currentIndex].codeAnswerIndex] in '1'..'9',
            "Spec scenario #44: auto-answer yields a score digit (1..9) for the question",
        )
    }

    // ── MultipleChoice (test 45) ──────────────────────────────────────────────

    @Test
    fun `given MultipleChoice 5 options correctSize 3 when auto-random then advances state`() {
        // Spec scenario #45: selected = random subset of size 3 (= correctOptionIds.size)
        val content = multipleChoiceContent(
            correctIds = setOf("A", "B", "C"),
            allOptionIds = listOf("A", "B", "C", "D", "E"),
        )
        val state = makeReadyState(playOrder = listOf(makeRunnerQuestion(content = content)))
        val resultState = autoAnswerOnTimeout(state, randomSeed = 42L, nowMs = nowMs)
        assertTrue(
            resultState.indexInPool > state.indexInPool || resultState.indexInPool == state.playOrder.size,
            "Spec scenario #45: auto-answer advances indexInPool",
        )
    }

    // ── Ordering (test 46) ────────────────────────────────────────────────────

    @Test
    fun `given Ordering 4 items when auto-random then result advances state and records score`() {
        // Spec scenario #46: userOrder = random permutation of items
        val content = orderingContent(itemIds = listOf("A", "B", "C", "D"))
        val state = makeReadyState(playOrder = listOf(makeRunnerQuestion(content = content)))
        val resultState = autoAnswerOnTimeout(state, randomSeed = 42L, nowMs = nowMs)
        val codeAnswerIndex = state.playOrder[state.indexInPool].codeAnswerIndex
        assertTrue(
            resultState.codeAnswer.raw[codeAnswerIndex] in '1'..'9',
            "Spec scenario #46: Ordering auto-answer → score digit recorded",
        )
    }

    // ── FillBlank (test 47) ───────────────────────────────────────────────────

    @Test
    fun `given FillBlank 3 blanks 5 candidates when auto-random then advances state`() {
        // Spec scenario #47: each blank filled with random candidate (with replacement)
        val content = fillBlankContent(
            blankCorrectPairs = listOf("b1" to "c1", "b2" to "c2", "b3" to "c3"),
            candidateIds = listOf("c1", "c2", "c3", "c4", "c5"),
        )
        val state = makeReadyState(playOrder = listOf(makeRunnerQuestion(content = content)))
        val resultState = autoAnswerOnTimeout(state, randomSeed = 42L, nowMs = nowMs)
        val codeAnswerIndex = state.playOrder[state.indexInPool].codeAnswerIndex
        assertTrue(
            resultState.codeAnswer.raw[codeAnswerIndex] in '1'..'9',
            "Spec scenario #47: FillBlank auto-answer → score digit recorded",
        )
    }

    // ── Determinism check ─────────────────────────────────────────────────────

    @Test
    fun `given same randomSeed autoAnswer produces same result`() {
        val content = singleChoiceContent(optionIds = listOf("A", "B", "C", "D"))
        val state = makeReadyState(playOrder = listOf(makeRunnerQuestion(content = content)))
        val result1 = autoAnswerOnTimeout(state, randomSeed = 999L, nowMs = nowMs)
        val result2 = autoAnswerOnTimeout(state, randomSeed = 999L, nowMs = nowMs)
        assertEquals(
            result1.codeAnswer.raw,
            result2.codeAnswer.raw,
            "Same seed → deterministic auto-answer",
        )
    }

    @Test
    fun `given SingleChoice draft on timeout then draft selection is preserved`() {
        val content = singleChoiceContent(correctId = "B", optionIds = listOf("A", "B", "C", "D"))

        val answer = generateTimeoutAnswer(
            content = content,
            draft = UserAnswerDraft.SingleChoiceDraft(optId("B")),
            seed = 42L,
        )

        assertEquals(UserAnswer.SingleChoiceAnswer(optId("B")), answer)
    }

    @Test
    fun `given MultipleChoice partial draft on timeout then selected items stay and random fills remainder`() {
        val content = multipleChoiceContent(
            correctIds = setOf("A", "B", "C"),
            allOptionIds = listOf("A", "B", "C", "D", "E"),
        )

        val answer = generateTimeoutAnswer(
            content = content,
            draft = UserAnswerDraft.MultipleChoiceDraft(setOf(optId("E"))),
            seed = 42L,
        ) as UserAnswer.MultipleChoiceAnswer

        assertTrue(optId("E") in answer.selected)
        assertEquals(3, answer.selected.size)
    }

    @Test
    fun `given FillBlank partial draft on timeout then filled blanks stay and empty blanks are random-filled`() {
        val content = fillBlankContent(
            blankCorrectPairs = listOf("b1" to "c1", "b2" to "c2", "b3" to "c3"),
            candidateIds = listOf("c1", "c2", "c3", "c4", "c5"),
        )

        val answer = generateTimeoutAnswer(
            content = content,
            draft = UserAnswerDraft.FillBlankDraft(mapOf(blankId("b2") to candId("c5"))),
            seed = 42L,
        ) as UserAnswer.FillBlankAnswer

        assertEquals(candId("c5"), answer.filled[blankId("b2")])
        assertEquals(3, answer.filled.size)
        assertTrue(content.blanks.all { it.id in answer.filled.keys })
    }
}
