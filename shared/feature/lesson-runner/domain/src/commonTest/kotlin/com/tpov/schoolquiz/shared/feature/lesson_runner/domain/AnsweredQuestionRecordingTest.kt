package com.tpov.schoolquiz.shared.feature.lesson_runner.domain

import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.autoAnswerOnTimeout
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.submitAnswer
import com.tpov.schoolquiz.shared.core.scoring.UserAnswer
import com.tpov.schoolquiz.shared.feature.question.domain.model.QuestionId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The attempt keeps only one digit per question, which is enough to score a lesson but not to
 * power spaced repetition, per-lesson statistics, survey distributions or bot detection. These
 * tests pin down the extra data the runner now records alongside that digit.
 */
class AnsweredQuestionRecordingTest {

    @Test
    fun `answer is recorded with the question it belongs to`() {
        val state = makeReadyState(
            playOrder = listOf(makeRunnerQuestion(id = "q-42", codeAnswerIndex = 3)),
            eligibleSize = 4,
        )

        val next = submitAnswer(state, UserAnswer.SingleChoiceAnswer(optId("A")), nowMs = 1_000L)

        assertEquals(1, next.answers.size)
        val answered = next.answers.single()
        assertEquals(QuestionId("q-42"), answered.questionId)
        assertEquals(3, answered.codeAnswerIndex)
        assertEquals(1_000L, answered.answeredAtMs)
    }

    @Test
    fun `the chosen answer itself is kept, not just the score`() {
        // Without this a survey could never report which option people picked.
        val state = makeReadyState()
        val chosen = UserAnswer.SingleChoiceAnswer(optId("B"))

        val next = submitAnswer(state, chosen, nowMs = 500L)

        assertEquals(chosen, next.answers.single().answer)
    }

    @Test
    fun `recorded score matches the digit written into codeAnswer`() {
        val state = makeReadyState(
            playOrder = listOf(makeRunnerQuestion(codeAnswerIndex = 0)),
            eligibleSize = 1,
        )

        // singleChoiceContent's correct option is A → digit 9.
        val next = submitAnswer(state, UserAnswer.SingleChoiceAnswer(optId("A")), nowMs = 1L)

        val answered = next.answers.single()
        assertEquals(9, answered.score.raw)
        assertEquals('9', next.codeAnswer.raw[answered.codeAnswerIndex])
    }

    @Test
    fun `duration is measured from when the question appeared`() {
        val state = makeReadyState(questionStartedAtMs = 1_000L)

        val next = submitAnswer(state, UserAnswer.SingleChoiceAnswer(optId("A")), nowMs = 4_500L)

        assertEquals(3_500L, next.answers.single().durationMs)
    }

    @Test
    fun `answering starts the clock for the next question`() {
        val state = makeReadyState(
            playOrder = listOf(
                makeRunnerQuestion(id = "q1", codeAnswerIndex = 0),
                makeRunnerQuestion(id = "q2", codeAnswerIndex = 1),
            ),
            eligibleSize = 2,
            questionStartedAtMs = 1_000L,
        )

        val afterFirst = submitAnswer(state, UserAnswer.SingleChoiceAnswer(optId("A")), nowMs = 3_000L)
        val afterSecond = submitAnswer(afterFirst, UserAnswer.SingleChoiceAnswer(optId("A")), nowMs = 5_000L)

        assertEquals(2_000L, afterFirst.answers[0].durationMs)
        assertEquals(2_000L, afterSecond.answers[1].durationMs)
    }

    @Test
    fun `unknown start time reports zero duration rather than a bogus one`() {
        // Restored sessions have no start timestamp; reporting nowMs as the duration would
        // silently poison the timing signal used to spot automated play.
        val state = makeReadyState(questionStartedAtMs = 0L)

        val next = submitAnswer(state, UserAnswer.SingleChoiceAnswer(optId("A")), nowMs = 9_999L)

        assertEquals(0L, next.answers.single().durationMs)
    }

    @Test
    fun `timer answers are marked, manual answers are not`() {
        val state = makeReadyState(
            playOrder = listOf(
                makeRunnerQuestion(id = "q1", codeAnswerIndex = 0),
                makeRunnerQuestion(id = "q2", codeAnswerIndex = 1),
            ),
            eligibleSize = 2,
        )

        val manual = submitAnswer(state, UserAnswer.SingleChoiceAnswer(optId("A")), nowMs = 100L)
        val timedOut = autoAnswerOnTimeout(manual, randomSeed = 7L, nowMs = 200L)

        assertFalse(manual.answers[0].wasTimeout)
        assertTrue(timedOut.answers[1].wasTimeout)
    }

    @Test
    fun `answers accumulate in the order they were given`() {
        val state = makeReadyState(
            playOrder = listOf(
                makeRunnerQuestion(id = "q1", codeAnswerIndex = 0),
                makeRunnerQuestion(id = "q2", codeAnswerIndex = 1),
                makeRunnerQuestion(id = "q3", codeAnswerIndex = 2),
            ),
            eligibleSize = 3,
        )

        var current = state
        repeat(3) { current = submitAnswer(current, UserAnswer.SingleChoiceAnswer(optId("A")), nowMs = 10L) }

        assertEquals(
            listOf(QuestionId("q1"), QuestionId("q2"), QuestionId("q3")),
            current.answers.map { it.questionId },
        )
    }
}
