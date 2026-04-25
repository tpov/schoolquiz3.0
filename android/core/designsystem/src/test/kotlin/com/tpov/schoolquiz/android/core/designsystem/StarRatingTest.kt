package com.tpov.schoolquiz.android.core.designsystem

import com.tpov.schoolquiz.android.core.designsystem.components.starIsFilled
import com.tpov.schoolquiz.android.core.designsystem.components.starIsPartial
import com.tpov.schoolquiz.android.core.designsystem.components.starPartialFraction
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * JVM unit tests for StarRating star-state logic (AC#26, AC#27).
 *
 * Tests pure functions starIsFilled / starIsPartial / starPartialFraction — no Android runtime needed.
 * Spec: docs/features/home-and-my-quests/0-spec.md FR#10
 *
 * NOTE: Pixel-level rendering of PartialStarIcon (Canvas clip correctness) requires
 * instrumented screenshot test (Paparazzi or compose-ui-test screenshot). Not currently
 * in project test infra — tracked as Open Question H8.
 */
class StarRatingTest {

    // AC#26: rating=2.7 → star0 full, star1 full, star2 partial
    @Test
    fun `rating 2_7 star0 is filled`() {
        assertTrue(starIsFilled(0, 2.7f))
    }

    @Test
    fun `rating 2_7 star1 is filled`() {
        assertTrue(starIsFilled(1, 2.7f))
    }

    @Test
    fun `rating 2_7 star2 is partial not filled`() {
        assertFalse(starIsFilled(2, 2.7f))
        assertTrue(starIsPartial(2, 2.7f))
    }

    // AC#27: rating=null → all outline (no filled, no partial)
    @Test
    fun `rating null all stars outline`() {
        repeat(3) { index ->
            assertFalse(starIsFilled(index, null), "star$index should not be filled")
            assertFalse(starIsPartial(index, null), "star$index should not be partial")
        }
    }

    // rating=0 → all outline (same as null display)
    @Test
    fun `rating 0 all stars outline`() {
        repeat(3) { index ->
            assertFalse(starIsFilled(index, 0f), "star$index should not be filled")
            assertFalse(starIsPartial(index, 0f), "star$index should not be partial")
        }
    }

    // rating=3.0 → all 3 filled
    @Test
    fun `rating 3_0 all stars filled`() {
        repeat(3) { index ->
            assertTrue(starIsFilled(index, 3.0f), "star$index should be filled")
            assertFalse(starIsPartial(index, 3.0f), "star$index should not be partial")
        }
    }

    // rating=1.5 → star0 full, star1 partial, star2 outline
    @Test
    fun `rating 1_5 star0 filled star1 partial star2 outline`() {
        assertTrue(starIsFilled(0, 1.5f))
        assertFalse(starIsFilled(1, 1.5f))
        assertTrue(starIsPartial(1, 1.5f))
        assertFalse(starIsFilled(2, 1.5f))
        assertFalse(starIsPartial(2, 1.5f))
    }

    // --- starPartialFraction tests (AC#26 visual: fraction drives clip width) ---

    @Test
    fun `rating 2_7 star2 partial fraction is 0_7`() {
        // star2 fraction = 2.7 - 2.0 = 0.7 → PartialStarIcon clips to 70% width
        assertEquals(0.7f, starPartialFraction(2, 2.7f), 0.001f)
    }

    @Test
    fun `rating 1_5 star1 partial fraction is 0_5`() {
        assertEquals(0.5f, starPartialFraction(1, 1.5f), 0.001f)
    }

    @Test
    fun `partial fraction for filled star returns 0`() {
        // star0 is fully filled for rating=2.7 → fraction 0 (not partial)
        assertEquals(0f, starPartialFraction(0, 2.7f), 0.001f)
    }

    @Test
    fun `partial fraction for outline star returns 0`() {
        // star2 is outline for rating=0.5 → fraction 0
        assertEquals(0f, starPartialFraction(2, 0.5f), 0.001f)
    }

    @Test
    fun `partial fraction for null rating returns 0`() {
        assertEquals(0f, starPartialFraction(0, null), 0.001f)
    }

    @Test
    fun `rating 0_1 star0 partial fraction is 0_1`() {
        assertEquals(0.1f, starPartialFraction(0, 0.1f), 0.001f)
    }

    @Test
    fun `rating 2_9 star2 partial fraction is 0_9`() {
        assertEquals(0.9f, starPartialFraction(2, 2.9f), 0.001f)
    }
}
