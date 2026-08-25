package com.tpov.schoolquiz.shared.feature.internet.profile.domain

import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.ProfileActivityRatings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProfileActivityRatingsTest {
    /** Nothing done anywhere leaves the ring at the centre rather than dividing by zero. */
    @Test
    fun emptyRatingsGiveSixZeroAxes() {
        val axes = ProfileActivityRatings().axes

        assertEquals(List(6) { 0f }, axes)
    }

    /**
     * The axes are shares of the account's own best, not absolute counts.
     *
     * The six are measured in different units — questions, seconds, points — so the only reading
     * that survives putting them on one chart is how far along each is against the strongest.
     */
    @Test
    fun axesAreScaledAgainstTheLargestFigure() {
        val axes =
            ProfileActivityRatings(
                questionsAsked = 200,
                questionsRight = 100,
                quizzes = 50,
            ).axes

        assertEquals(1f, axes[0])
        assertEquals(0.5f, axes[1])
        assertEquals(0.25f, axes[5])
        assertEquals(0f, axes[2])
    }

    @Test
    fun everyAxisStaysWithinTheChart() {
        val axes =
            ProfileActivityRatings(
                questionsAsked = 7,
                questionsRight = 3,
                timeInQuiz = 9_000,
                timeInChat = 1,
                smsPoints = 44,
                quizzes = 2,
            ).axes

        assertEquals(6, axes.size)
        assertTrue(axes.all { it in 0f..1f })
    }

    /** A single non-zero figure is that account's own peak, so it draws at full reach. */
    @Test
    fun oneFigureAloneReachesTheEdge() {
        val axes = ProfileActivityRatings(quizzes = 3).axes

        assertEquals(1f, axes[5])
        assertEquals(List(5) { 0f }, axes.take(5))
    }
}
