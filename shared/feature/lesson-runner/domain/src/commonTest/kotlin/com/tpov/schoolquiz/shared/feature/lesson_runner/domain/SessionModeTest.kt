package com.tpov.schoolquiz.shared.feature.lesson_runner.domain

import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
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
    fun `an exam gives less time than practice for the same question`() {
        val content = singleChoiceContent(text = "A".repeat(200))

        val learning = computeTimer(content, Difficulty.EASY, TimerCoefficients.Default, SessionMode.LEARNING)
        val exam = computeTimer(content, Difficulty.EASY, TimerCoefficients.Default, SessionMode.EXAM)

        assertTrue(exam.seconds < learning.seconds, "exam=${exam.seconds} learning=${learning.seconds}")
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
