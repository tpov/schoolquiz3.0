package com.tpov.schoolquiz.shared.feature.lesson_runner.domain

import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.SpacedRepetition
import com.tpov.schoolquiz.shared.core.scoring.Score
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpacedRepetitionTest {

    private val day = 24L * 60L * 60L * 1000L

    @Test
    fun `runner digits map onto the SM-2 quality scale`() {
        // 1 = nothing recalled, 9 = perfect.
        assertEquals(0, SpacedRepetition.qualityOf(Score(1)))
        assertEquals(5, SpacedRepetition.qualityOf(Score(9)))
        // The scale in between is monotonic — a better answer never scores worse.
        val qualities = (1..9).map { SpacedRepetition.qualityOf(Score(it)) }
        assertEquals(qualities.sorted(), qualities)
    }

    @Test
    fun `first correct answer comes back the next day, not in a month`() {
        // The whole point of the curve: fresh material needs an early repetition.
        val state = SpacedRepetition.next(previous = null, score = Score(9), answeredAtMs = 0L)

        assertEquals(1, state.intervalDays)
        assertEquals(1, state.repetitions)
        assertEquals(day, state.nextReviewAtMs)
    }

    @Test
    fun `intervals grow one, six, then by ease factor`() {
        var state = SpacedRepetition.next(null, Score(9), 0L)
        assertEquals(1, state.intervalDays)

        state = SpacedRepetition.next(state, Score(9), state.nextReviewAtMs)
        assertEquals(6, state.intervalDays)

        // Third and later: previous interval × ease. Ease has risen above the 2.5 start.
        state = SpacedRepetition.next(state, Score(9), state.nextReviewAtMs)
        assertTrue(state.intervalDays > 6, "expected growth, got ${state.intervalDays}")
    }

    @Test
    fun `a wrong answer sends the question back to tomorrow`() {
        var state = SpacedRepetition.next(null, Score(9), 0L)
        state = SpacedRepetition.next(state, Score(9), state.nextReviewAtMs)
        assertEquals(6, state.intervalDays)

        val lapsedAt = state.nextReviewAtMs
        val lapsed = SpacedRepetition.next(state, Score(1), lapsedAt)

        assertEquals(1, lapsed.intervalDays)
        assertEquals(0, lapsed.repetitions)
        assertEquals(lapsedAt + day, lapsed.nextReviewAtMs)
    }

    @Test
    fun `repeated failures make the question come back harder, not softer`() {
        // Ease keeps dropping, so once it starts passing again the intervals grow slowly.
        var state = SpacedRepetition.next(null, Score(1), 0L)
        val firstEase = state.easeFactorMilli
        state = SpacedRepetition.next(state, Score(1), day)
        val secondEase = state.easeFactorMilli

        assertTrue(secondEase < firstEase, "ease must fall after another lapse")
    }

    @Test
    fun `ease never falls below the SM-2 floor`() {
        var state = SpacedRepetition.next(null, Score(1), 0L)
        repeat(50) { state = SpacedRepetition.next(state, Score(1), day * (it + 1)) }

        assertEquals(SpacedRepetition.MIN_EASE_MILLI, state.easeFactorMilli)
        // And the interval stays sane rather than collapsing to zero.
        assertTrue(state.intervalDays >= 1)
    }

    @Test
    fun `a perfect streak schedules the question far out`() {
        var state = SpacedRepetition.next(null, Score(9), 0L)
        repeat(5) { state = SpacedRepetition.next(state, Score(9), state.nextReviewAtMs) }

        // Six perfect recalls should push this well past a month.
        assertTrue(state.intervalDays > 30, "expected a long interval, got ${state.intervalDays}")
    }

    @Test
    fun `a barely passing answer still advances but slower than a perfect one`() {
        val perfect = generateSequence(SpacedRepetition.next(null, Score(9), 0L)) {
            SpacedRepetition.next(it, Score(9), it.nextReviewAtMs)
        }.elementAt(3)

        val mediocre = generateSequence(SpacedRepetition.next(null, Score(6), 0L)) {
            SpacedRepetition.next(it, Score(6), it.nextReviewAtMs)
        }.elementAt(3)

        assertTrue(
            mediocre.intervalDays < perfect.intervalDays,
            "mediocre=${mediocre.intervalDays} perfect=${perfect.intervalDays}",
        )
    }
}
