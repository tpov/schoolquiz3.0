package com.tpov.schoolquiz.shared.feature.lesson_runner.domain

import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.core.question_schema.QuestionContent
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computeTimer
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.SessionMode
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.TimerCoefficients
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionModeTest {

    @Test
    fun `answers are revealed only while practising an easy question`() {
        // The matrix from ADR-0005. Hard questions are the assessment even during practice.
        assertTrue(SessionMode.LEARNING.revealsCorrectAnswer(Difficulty.EASY))
        assertFalse(SessionMode.LEARNING.revealsCorrectAnswer(Difficulty.HARD))
        assertFalse(SessionMode.EXAM.revealsCorrectAnswer(Difficulty.EASY))
        assertFalse(SessionMode.EXAM.revealsCorrectAnswer(Difficulty.HARD))
    }

    @Test
    fun `an exam times the question without the exam factor`() {
        // EXAM:AD-3: «Do not multiply by examFactor0.75» — every exam question, including the five
        // EASY theme positions of a mixed theme test, gets the ordinary allowance for its
        // difficulty. 232 chars × 0.36 = 84s both in practice and in an exam (0.75 would cut it).
        val content = singleChoiceContent(text = "A".repeat(200))

        val learning = computeTimer(content, Difficulty.EASY, TimerCoefficients.Default, SessionMode.LEARNING)
        val exam = computeTimer(content, Difficulty.EASY, TimerCoefficients.Default, SessionMode.EXAM)

        assertEquals(learning, exam, "exam=${exam.seconds} learning=${learning.seconds}")
        assertEquals(84, exam.seconds, "round(232 × 0.36) with no session-mode factor")
    }

    @Test
    fun `an exam hard question follows the ordinary hard rule`() {
        // EXAM:AD-3 timer rule: seconds = max(5, roundHalfUp(chars × 0.24)); 100 chars → 24s,
        // while the inherited examFactor0.75 would have squeezed it to 18s.
        val content = QuestionContent.SingleChoice(
            id = "q1",
            difficulty = Difficulty.HARD,
            text = "A".repeat(96),
            imageUrl = null,
            options = listOf(
                QuestionContent.Option(optId("A"), "A"),
                QuestionContent.Option(optId("B"), "B"),
                QuestionContent.Option(optId("C"), "C"),
                QuestionContent.Option(optId("D"), "D"),
            ),
            correctOptionId = optId("A"),
        )

        val exam = computeTimer(content, Difficulty.HARD, TimerCoefficients.Default, SessionMode.EXAM)

        assertEquals(24, exam.seconds, "expected max(5, round(100 × 0.24)) = 24, got ${exam.seconds}")
    }

    @Test
    fun `practice is the default so existing behaviour is unchanged`() {
        val content = singleChoiceContent(text = "A".repeat(200))

        assertEquals(
            computeTimer(content, Difficulty.EASY, TimerCoefficients.Default),
            computeTimer(content, Difficulty.EASY, TimerCoefficients.Default, SessionMode.LEARNING),
        )
    }

    @Test
    fun `the timer floor still applies in an exam`() {
        // Short questions must not end up with an unusable one-second allowance.
        val content = singleChoiceContent(text = "?")

        val exam = computeTimer(content, Difficulty.HARD, TimerCoefficients.Default, SessionMode.EXAM)

        assertTrue(exam.seconds >= 5, "expected the 5s floor, got ${exam.seconds}")
    }
}
