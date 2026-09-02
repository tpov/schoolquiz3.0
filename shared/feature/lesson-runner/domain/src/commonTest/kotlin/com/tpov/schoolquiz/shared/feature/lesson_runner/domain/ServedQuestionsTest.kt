package com.tpov.schoolquiz.shared.feature.lesson_runner.domain

import com.tpov.schoolquiz.shared.core.scoring.CodeAnswer
import com.tpov.schoolquiz.shared.core.scoring.UserAnswer
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeClock
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeLessonAttemptRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake.FakeLessonRatingRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.selectSubset
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.submitAnswer
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.RunnerQuestion
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ServedQuestion
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.toServedQuestions
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.state.RunnerState
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.AbortAttemptUseCase
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.CompleteAttemptUseCase
import com.tpov.schoolquiz.shared.feature.question.domain.model.QuestionId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The server can only place each digit honestly if it is told which questions were put to the
 * player: `'0'` is "not shown", and inferring "not shown" from "not submitted" let a client score
 * 100% by staying silent about its wrong answers. So the attempt names its served questions, and
 * these tests pin the one fact that makes the list trustworthy — on both paths the served positions
 * are exactly the non-`'0'` positions of the codeAnswer the attempt is saved with.
 */
class ServedQuestionsTest {

    private val attemptRepo = FakeLessonAttemptRepository()
    private var idCounter = 0
    private val idProvider = { AttemptId("attempt-${idCounter++}") }

    private val completeUseCase = CompleteAttemptUseCase(
        attemptRepository = attemptRepo,
        ratingRepository = FakeLessonRatingRepository(),
        clock = FakeClock(),
        attemptIdProvider = idProvider,
    )

    private val abortUseCase = AbortAttemptUseCase(
        attemptRepository = attemptRepo,
        clock = FakeClock(),
        attemptIdProvider = idProvider,
    )

    private val lastSave: FakeLessonAttemptRepository.Save
        get() = assertNotNull(attemptRepo.lastSave, "the use case must have saved")

    // ── Completed run ─────────────────────────────────────────────────────────

    @Test
    fun `completed run serves every play-order question and nothing outside it`() = runTest {
        val playOrder = dealtPlayOrder(poolSize = 30, playSize = 20)
        val finished = answerFirst(20, makeReadyState(playOrder = playOrder, eligibleSize = 30))

        val result = completeUseCase(finished)

        assertIs<RunnerState.Completed>(result)
        val saved = lastSave
        assertEquals(20, saved.served.size)
        assertServedMatchesCodeAnswer(saved.served, saved.attempt.codeAnswer, playOrder)
        // The ten positions outside the play order are neither served nor scored.
        val outside = (0 until 30).toSet() - playOrder.map { it.codeAnswerIndex }.toSet()
        assertEquals(10, outside.size)
        outside.forEach { position ->
            assertEquals('0', saved.attempt.codeAnswer.raw[position], "position $position was never shown")
            assertTrue(saved.served.none { it.codeAnswerIndex == position }, "position $position must be absent")
        }
    }

    @Test
    fun `completed run serves the same question and position each answer row claims`() = runTest {
        val playOrder = dealtPlayOrder(poolSize = 8, playSize = 5)
        val finished = answerFirst(5, makeReadyState(playOrder = playOrder, eligibleSize = 8))

        completeUseCase(finished)

        val saved = lastSave
        val fromAnswers = saved.answers
            .map { ServedQuestion(it.questionId, it.codeAnswerIndex) }
            .sortedBy { it.codeAnswerIndex }
        assertEquals(fromAnswers, saved.served)
    }

    // ── Abandoned run ─────────────────────────────────────────────────────────

    @Test
    fun `abandoned run serves the whole play order not just what was reached`() = runTest {
        val playOrder = dealtPlayOrder(poolSize = 30, playSize = 20)
        val partway = answerFirst(3, makeReadyState(playOrder = playOrder, eligibleSize = 30))

        val result = abortUseCase(partway)

        assertIs<RunnerState.Aborted>(result)
        val saved = lastSave
        assertEquals(3, saved.answers.size, "only three were answered")
        assertEquals(20, saved.served.size, "but all twenty were dealt into the play order")
        assertServedMatchesCodeAnswer(saved.served, saved.attempt.codeAnswer, playOrder)
        // The abort path writes '1' for the unreached ones, which is why they count as shown.
        playOrder.drop(3).forEach { unreached ->
            assertEquals('1', saved.attempt.codeAnswer.raw[unreached.codeAnswerIndex])
        }
    }

    @Test
    fun `a lesson opened and closed again still serves its play order`() = runTest {
        val playOrder = dealtPlayOrder(poolSize = 12, playSize = 7)
        val untouched = makeReadyState(playOrder = playOrder, eligibleSize = 12)

        abortUseCase(untouched)

        val saved = lastSave
        assertEquals(0, saved.answers.size)
        assertEquals(7, saved.served.size)
        assertServedMatchesCodeAnswer(saved.served, saved.attempt.codeAnswer, playOrder)
    }

    // ── Shape ─────────────────────────────────────────────────────────────────

    @Test
    fun `served list is sorted by position whatever order the questions were dealt in`() {
        val dealtBackwards = (4 downTo 0).map { makeRunnerQuestion(id = "q$it", order = it, codeAnswerIndex = it) }

        val served = dealtBackwards.toServedQuestions()

        assertEquals(listOf(0, 1, 2, 3, 4), served.map { it.codeAnswerIndex })
        assertEquals(listOf("q0", "q1", "q2", "q3", "q4"), served.map { it.questionId.value })
    }

    @Test
    fun `served list refuses two questions at one position`() {
        // makeRunnerQuestion defaults every question to position 0 — one forgotten argument away.
        val twoAtZero = listOf(
            makeRunnerQuestion(id = "q0", codeAnswerIndex = 0),
            makeRunnerQuestion(id = "q1", codeAnswerIndex = 0),
        )

        assertFailsWith<IllegalArgumentException> { twoAtZero.toServedQuestions() }
    }

    @Test
    fun `served list refuses one question at two positions`() {
        val sameIdTwice = listOf(
            makeRunnerQuestion(id = "q0", codeAnswerIndex = 0),
            makeRunnerQuestion(id = "q0", codeAnswerIndex = 1),
        )

        assertFailsWith<IllegalArgumentException> { sameIdTwice.toServedQuestions() }
    }

    @Test
    fun `served question rejects a negative position`() {
        assertFailsWith<IllegalArgumentException> { ServedQuestion(QuestionId("q"), -1) }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * A pool of [poolSize] eligible questions with [playSize] of them dealt in random order — the
     * same shape StartLessonAttemptUseCase produces, through the same selection function.
     */
    private fun dealtPlayOrder(poolSize: Int, playSize: Int): List<RunnerQuestion.Valid> {
        val pool = (0 until poolSize).map { makeRunnerQuestion(id = "q$it", order = it, codeAnswerIndex = it) }
        return selectSubset(pool, playSize, seed = 12345L)
    }

    private fun answerFirst(count: Int, state: RunnerState.Ready): RunnerState.Ready {
        var current = state
        repeat(count) {
            current = submitAnswer(current, UserAnswer.SingleChoiceAnswer(optId("A")), nowMs = 1_000L)
        }
        return current
    }

    /** The Always invariant: served positions are exactly the non-'0' positions, sorted, unique, from the pool. */
    private fun assertServedMatchesCodeAnswer(
        served: List<ServedQuestion>,
        codeAnswer: CodeAnswer,
        playOrder: List<RunnerQuestion.Valid>,
    ) {
        val shown = codeAnswer.raw.indices.filter { codeAnswer.raw[it] != '0' }
        // One list against the other: exactly the non-'0' positions, ascending — hence sorted and unique.
        assertEquals(
            shown,
            served.map { it.codeAnswerIndex },
            "served must be the non-'0' positions of ${codeAnswer.raw}",
        )
        val byPosition = playOrder.associateBy { it.codeAnswerIndex }
        served.forEach { entry ->
            val question = assertNotNull(
                byPosition[entry.codeAnswerIndex],
                "position ${entry.codeAnswerIndex} is not in the play order",
            )
            assertEquals(
                question.sourceId,
                entry.questionId,
                "position ${entry.codeAnswerIndex} names the wrong question",
            )
        }
    }
}
