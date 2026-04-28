package com.tpov.schoolquiz.shared.feature.lesson_runner.domain

import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computeStars
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PercentScore
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Spec scenarios 21-29: Stars formula for EASY and HARD modes.
 * Tests `computeStars(percentScore, mode): Stars`.
 *
 * EASY: rawTenths = (percentScore * 20 + 50) / 100  → [0..20]
 * HARD: rawTenths = 20 + (percentScore * 10 + 50) / 100  → [20..30]
 */
class StarsComputeTest {

    // ── EASY mode (tests 21-25) ───────────────────────────────────────────────

    @Test
    fun `given EASY attempt percentScore 100 then Stars rawTenths 20`() {
        // Spec scenario #21: (100*20+50)/100 = 2050/100 = 20
        val stars = computeStars(PercentScore(100), Difficulty.EASY)
        assertEquals(20, stars.rawTenths, "Spec scenario #21")
    }

    @Test
    fun `given EASY attempt percentScore 50 then Stars rawTenths 10`() {
        // Spec scenario #22: (50*20+50)/100 = 1050/100 = 10
        val stars = computeStars(PercentScore(50), Difficulty.EASY)
        assertEquals(10, stars.rawTenths, "Spec scenario #22")
    }

    @Test
    fun `given EASY attempt percentScore 75 then Stars rawTenths 15`() {
        // Spec scenario #23: (75*20+50)/100 = 1550/100 = 15
        val stars = computeStars(PercentScore(75), Difficulty.EASY)
        assertEquals(15, stars.rawTenths, "Spec scenario #23")
    }

    @Test
    fun `given EASY attempt percentScore 33 then Stars rawTenths 7`() {
        // Spec scenario #24: (33*20+50)/100 = 710/100 = 7
        val stars = computeStars(PercentScore(33), Difficulty.EASY)
        assertEquals(7, stars.rawTenths, "Spec scenario #24")
    }

    @Test
    fun `given EASY attempt percentScore 0 then Stars rawTenths 0`() {
        // Spec scenario #25: (0*20+50)/100 = 50/100 = 0
        val stars = computeStars(PercentScore(0), Difficulty.EASY)
        assertEquals(0, stars.rawTenths, "Spec scenario #25")
    }

    // ── HARD mode (tests 26-29) ───────────────────────────────────────────────

    @Test
    fun `given HARD attempt percentScore 100 then Stars rawTenths 30`() {
        // Spec scenario #26: 20 + (100*10+50)/100 = 20 + 1050/100 = 20+10 = 30
        val stars = computeStars(PercentScore(100), Difficulty.HARD)
        assertEquals(30, stars.rawTenths, "Spec scenario #26")
    }

    @Test
    fun `given HARD attempt percentScore 50 then Stars rawTenths 25`() {
        // Spec scenario #27: 20 + (50*10+50)/100 = 20 + 550/100 = 20+5 = 25
        val stars = computeStars(PercentScore(50), Difficulty.HARD)
        assertEquals(25, stars.rawTenths, "Spec scenario #27")
    }

    @Test
    fun `given HARD attempt percentScore 80 then Stars rawTenths 28`() {
        // Spec scenario #28: 20 + (80*10+50)/100 = 20 + 850/100 = 20+8 = 28
        val stars = computeStars(PercentScore(80), Difficulty.HARD)
        assertEquals(28, stars.rawTenths, "Spec scenario #28")
    }

    @Test
    fun `given HARD attempt percentScore 0 then Stars rawTenths 20`() {
        // Spec scenario #29: HARD floor = 20; 20 + (0*10+50)/100 = 20+0 = 20
        val stars = computeStars(PercentScore(0), Difficulty.HARD)
        assertEquals(20, stars.rawTenths, "Spec scenario #29: HARD floor is 2.0 stars")
    }
}
