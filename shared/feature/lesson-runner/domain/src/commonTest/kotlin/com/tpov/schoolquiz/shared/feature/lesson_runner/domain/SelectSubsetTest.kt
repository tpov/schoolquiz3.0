package com.tpov.schoolquiz.shared.feature.lesson_runner.domain

import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.selectSubset
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.RunnerQuestion
import com.tpov.schoolquiz.shared.feature.question.domain.model.QuestionId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Spec scenarios 40-43, 75-76: Pool selection and subset determinism.
 * Tests `selectSubset(eligible, poolSize, seed): List<RunnerQuestion.Valid>`.
 *
 * Note: `selectSubset` implementation shuffles + takes; sorting of playOrder
 * happens in StartLessonAttemptUseCase. Spec #77 (duplicate order stability)
 * is tested in StartLessonAttemptUseCaseTest via playOrder output.
 */
class SelectSubsetTest {

    private fun makeValidQuestion(id: String, order: Int = 0, codeAnswerIndex: Int = 0) =
        RunnerQuestion.Valid(
            sourceId = QuestionId(id),
            order = order,
            codeAnswerIndex = codeAnswerIndex,
            content = singleChoiceContent(),
        )

    private fun eligibleList(size: Int): List<RunnerQuestion.Valid> =
        (1..size).map { makeValidQuestion("q$it", order = it, codeAnswerIndex = it - 1) }

    @Test
    fun `given eligibleSize 5 poolSize 20 when selectSubset then subset size 5`() {
        // Spec scenario #40: min(20, 5) = 5 → all included
        val subset = selectSubset(eligibleList(5), 20, seed = 12345L)
        assertEquals(5, subset.size, "Spec scenario #40")
    }

    @Test
    fun `given eligibleSize 30 poolSize 20 when selectSubset then subset size 20`() {
        // Spec scenario #41: min(20, 30) = 20
        val subset = selectSubset(eligibleList(30), 20, seed = 12345L)
        assertEquals(20, subset.size, "Spec scenario #41")
    }

    @Test
    fun `given same seed for two calls with identical eligible then same subset same order`() {
        // Spec scenarios #42 and #75: determinism — same seed, same order
        val eligible = eligibleList(30)
        val subset1 = selectSubset(eligible, 20, seed = 12345L)
        val subset2 = selectSubset(eligible, 20, seed = 12345L)
        assertEquals(
            subset1.map { it.sourceId.value },
            subset2.map { it.sourceId.value },
            "Spec scenarios #42/#75: same seed → identical subset in same order",
        )
    }

    @Test
    fun `given seed 12345 vs seed 67890 for eligibleSize 30 then results differ by at least one question`() {
        // Spec scenarios #43 and #76: different seeds → different subsets (pre-recorded fixture)
        val eligible = eligibleList(30)
        val ids1 = selectSubset(eligible, 20, seed = 12345L).map { it.sourceId }.toSet()
        val ids2 = selectSubset(eligible, 20, seed = 67890L).map { it.sourceId }.toSet()
        assertTrue(ids1 != ids2, "Spec scenarios #43/#76: different seeds → different subsets")
    }

    @Test
    fun `given empty eligible list then subset is empty`() {
        val subset = selectSubset(emptyList(), 20, seed = 12345L)
        assertEquals(0, subset.size, "Empty eligible → empty subset")
    }
}
