package com.tpov.schoolquiz.shared.feature.lesson_runner.domain

import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computeBestStars
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computeHardUnlocked
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Stars
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Spec scenarios 30-35a: bestStars and hardUnlocked logic.
 * Tests `computeBestStars(attempts): Stars` and `computeHardUnlocked(attempts): Boolean`.
 */
class BestStarsHardUnlockedTest {

    // ── computeBestStars (tests 30-32) ────────────────────────────────────────

    @Test
    fun `given no attempts then bestStars is Stars 0`() {
        // Spec scenario #30
        val result = computeBestStars(emptyList())
        assertEquals(Stars(0), result, "Spec scenario #30: no attempts → Stars(0)")
    }

    @Test
    fun `given 3 attempts with rawTenths 10 15 20 then bestStars is Stars 20`() {
        // Spec scenario #31
        val attempts = listOf(
            makeAttempt(codeAnswer = "5", percentScore = 50, mode = Difficulty.EASY),
            makeAttempt(codeAnswer = "7", percentScore = 75, mode = Difficulty.EASY),
            makeAttempt(codeAnswer = "9", percentScore = 100, mode = Difficulty.EASY),
        )
        val result = computeBestStars(attempts)
        assertEquals(Stars(20), result, "Spec scenario #31: max over attempts → Stars(20)")
    }

    @Test
    fun `given EASY Stars 15 and HARD Stars 25 then bestStars is Stars 25`() {
        // Spec scenario #32
        val attempts = listOf(
            makeAttempt(codeAnswer = "7", percentScore = 75, mode = Difficulty.EASY),
            makeAttempt(codeAnswer = "9", percentScore = 50, mode = Difficulty.HARD),
        )
        val result = computeBestStars(attempts)
        assertEquals(Stars(25), result, "Spec scenario #32: HARD 25 > EASY 15 → Stars(25)")
    }

    // ── computeHardUnlocked (tests 33-35a) ───────────────────────────────────

    @Test
    fun `given no EASY attempt with allShownAnswersAre9 then hardUnlocked is false`() {
        // Spec scenario #33
        val result = computeHardUnlocked(emptyList())
        assertFalse(result, "Spec scenario #33: no attempts → hardUnlocked = false")
    }

    @Test
    fun `given EASY attempt with allShownAnswersAre9 true then hardUnlocked is true`() {
        // Spec scenario #34: codeAnswer all '9' → allShownAnswersAre9 = true
        val attempts = listOf(
            makeAttempt(codeAnswer = "9999", percentScore = 100, mode = Difficulty.EASY),
        )
        val result = computeHardUnlocked(attempts)
        assertTrue(result, "Spec scenario #34: EASY perfect attempt → hardUnlocked = true")
    }

    @Test
    fun `given EASY attempt where one digit is 8 then hardUnlocked is false`() {
        // Spec scenario #35: '8' is not '9' → allShownAnswersAre9 = false → not unlocked
        val attempts = listOf(
            makeAttempt(codeAnswer = "9989", percentScore = 95, mode = Difficulty.EASY),
        )
        val result = computeHardUnlocked(attempts)
        assertFalse(result, "Spec scenario #35: digit '8' breaks allShownAnswersAre9")
    }

    @Test
    fun `given HARD attempt exists without EASY perfect then hardUnlocked is false`() {
        // Spec scenario #35a: HARD attempt (floor Stars(20)) does NOT unlock
        val attempts = listOf(
            makeAttempt(codeAnswer = "1111", percentScore = 0, mode = Difficulty.HARD),
        )
        val result = computeHardUnlocked(attempts)
        assertFalse(result, "Spec scenario #35a: HARD attempt never unlocks, only EASY allShownAnswersAre9 does")
    }

    @Test
    fun `given HARD attempt with all 9 exists but no EASY perfect then hardUnlocked is false`() {
        // Spec scenario #35a variant: even HARD perfect does not unlock
        val attempts = listOf(
            makeAttempt(codeAnswer = "9999", percentScore = 100, mode = Difficulty.HARD),
        )
        val result = computeHardUnlocked(attempts)
        assertFalse(result, "Spec scenario #35a: HARD perfect attempt does NOT unlock (only EASY allShownAnswersAre9 does)")
    }

    @Test
    fun `given mix of EASY imperfect and HARD attempts then hardUnlocked is false`() {
        // Spec: HARD unlock requires EASY with allShownAnswersAre9 specifically
        val attempts = listOf(
            makeAttempt(codeAnswer = "8999", percentScore = 90, mode = Difficulty.EASY),
            makeAttempt(codeAnswer = "9999", percentScore = 100, mode = Difficulty.HARD),
        )
        val result = computeHardUnlocked(attempts)
        assertFalse(result, "EASY imperfect + HARD perfect → still false")
    }
}
