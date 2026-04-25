package com.tpov.schoolquiz.shared.feature.quest.domain

import com.tpov.schoolquiz.shared.feature.quest.domain.logic.StarFill
import com.tpov.schoolquiz.shared.feature.quest.domain.logic.computeStarFills
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Domain Test Scenarios for [computeStarFills] — pure star-rating logic.
 *
 * Covers scenarios 35-40 from docs/features/home-and-my-quests/0-spec.md
 * § "Domain Test Scenarios":
 *   35: averageRating=0.0  → 3 Empty
 *   36: averageRating=1.5  → 1 Full + 1 Partial(5) + 1 Empty
 *   37: averageRating=2.7  → 2 Full + Partial(7)
 *   38: averageRating=3.0  → 3 Full
 *   39: averageRating=null → 3 Empty + hasNoRatings=true
 *   40: averageRating=0.35 → Partial(3) + 2 Empty
 */
class StarRatingTest {

    // ── Scenario 35 ──────────────────────────────────────────────────────────
    @Test
    fun `scenario 35 averageRating 0_0 produces all 3 empty stars`() {
        val result = computeStarFills(averageRating = 0.0f)

        assertEquals(3, result.fills.size)
        assertEquals(StarFill.Empty, result.fills[0])
        assertEquals(StarFill.Empty, result.fills[1])
        assertEquals(StarFill.Empty, result.fills[2])
        assertFalse(result.hasNoRatings)
    }

    // ── Scenario 36 ──────────────────────────────────────────────────────────
    @Test
    fun `scenario 36 averageRating 1_5 produces 1 full + partial 5 + empty`() {
        val result = computeStarFills(averageRating = 1.5f)

        assertEquals(3, result.fills.size)
        assertEquals(StarFill.Full, result.fills[0])
        assertEquals(StarFill.Partial(5), result.fills[1])
        assertEquals(StarFill.Empty, result.fills[2])
        assertFalse(result.hasNoRatings)
    }

    // ── Scenario 37 ──────────────────────────────────────────────────────────
    @Test
    fun `scenario 37 averageRating 2_7 produces 2 full + partial 7`() {
        val result = computeStarFills(averageRating = 2.7f)

        assertEquals(3, result.fills.size)
        assertEquals(StarFill.Full, result.fills[0])
        assertEquals(StarFill.Full, result.fills[1])
        assertEquals(StarFill.Partial(7), result.fills[2])
        assertFalse(result.hasNoRatings)
    }

    // ── Scenario 38 ──────────────────────────────────────────────────────────
    @Test
    fun `scenario 38 averageRating 3_0 produces all 3 full stars`() {
        val result = computeStarFills(averageRating = 3.0f)

        assertEquals(3, result.fills.size)
        assertEquals(StarFill.Full, result.fills[0])
        assertEquals(StarFill.Full, result.fills[1])
        assertEquals(StarFill.Full, result.fills[2])
        assertFalse(result.hasNoRatings)
    }

    // ── Scenario 39 ──────────────────────────────────────────────────────────
    @Test
    fun `scenario 39 averageRating null produces 3 empty stars and hasNoRatings true`() {
        val result = computeStarFills(averageRating = null)

        assertEquals(3, result.fills.size)
        assertEquals(StarFill.Empty, result.fills[0])
        assertEquals(StarFill.Empty, result.fills[1])
        assertEquals(StarFill.Empty, result.fills[2])
        assertTrue(result.hasNoRatings)
    }

    // ── Scenario 40 ──────────────────────────────────────────────────────────
    // GIVEN averageRating=0.35f WHEN compute THEN 0 full + fractional 1st + 2 empty
    @Test
    fun `scenario 40 averageRating 0_35 produces partial first star and 2 empty`() {
        val result = computeStarFills(averageRating = 0.35f)

        assertEquals(3, result.fills.size)
        // 0.35 * 10 = 3.5 → integer = 3 tenths for star 0
        assertEquals(StarFill.Partial(3), result.fills[0])
        assertEquals(StarFill.Empty, result.fills[1])
        assertEquals(StarFill.Empty, result.fills[2])
        assertFalse(result.hasNoRatings)
    }

    // ── Additional edge cases ─────────────────────────────────────────────────

    @Test
    fun `StarFill Partial with tenths 0 throws`() {
        assertFailsWith<IllegalArgumentException> {
            StarFill.Partial(0)
        }
    }

    @Test
    fun `StarFill Partial with tenths 10 throws`() {
        assertFailsWith<IllegalArgumentException> {
            StarFill.Partial(10)
        }
    }

    @Test
    fun `StarFill Partial with tenths 9 constructs`() {
        val fill = StarFill.Partial(9)
        assertEquals(9, fill.tenths)
    }

    @Test
    fun `StarFill Partial with tenths 1 constructs`() {
        val fill = StarFill.Partial(1)
        assertEquals(1, fill.tenths)
    }

    @Test
    fun `computeStarFills rating 1_0 produces 1 full + 2 empty`() {
        val result = computeStarFills(averageRating = 1.0f)

        assertEquals(StarFill.Full, result.fills[0])
        assertEquals(StarFill.Empty, result.fills[1])
        assertEquals(StarFill.Empty, result.fills[2])
    }

    @Test
    fun `computeStarFills rating 2_0 produces 2 full + 1 empty`() {
        val result = computeStarFills(averageRating = 2.0f)

        assertEquals(StarFill.Full, result.fills[0])
        assertEquals(StarFill.Full, result.fills[1])
        assertEquals(StarFill.Empty, result.fills[2])
    }

    @Test
    fun `computeStarFills clamping above max does not throw`() {
        val result = computeStarFills(averageRating = 5.0f) // clamped to 3.0
        assertEquals(StarFill.Full, result.fills[0])
        assertEquals(StarFill.Full, result.fills[1])
        assertEquals(StarFill.Full, result.fills[2])
    }

    @Test
    fun `computeStarFills clamping below min does not throw`() {
        val result = computeStarFills(averageRating = -1.0f) // clamped to 0.0
        assertEquals(StarFill.Empty, result.fills[0])
        assertEquals(StarFill.Empty, result.fills[1])
        assertEquals(StarFill.Empty, result.fills[2])
    }
}
