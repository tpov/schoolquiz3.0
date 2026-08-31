package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic

import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.RepetitionState
import com.tpov.schoolquiz.shared.core.scoring.Score

/**
 * Spaced repetition, SM-2.
 *
 * The schedule follows the forgetting curve: each successful recall pushes the next showing
 * further away, a lapse brings it back to the next day. The gap grows by an *ease factor* that
 * itself moves with how well the question is recalled, so an item a person keeps fumbling
 * stays frequent while an easy one drifts out of the way.
 *
 * A flat "correct → +30 days" rule looks similar but behaves worse: fresh material gets its first
 * repetition a month late, which is well past the point where most of it is forgotten.
 *
 * Pure functions — no clock, no storage — so the schedule is testable on its own.
 */
object SpacedRepetition {

    /** Ease factors are stored scaled by 1000 to stay integers. */
    const val EASE_SCALE = 1000

    /** SM-2's starting ease (2.5). */
    const val INITIAL_EASE_MILLI = 2500

    /** SM-2 never lets the ease fall below 1.3, or intervals would collapse to nothing. */
    const val MIN_EASE_MILLI = 1300

    const val FIRST_INTERVAL_DAYS = 1
    const val SECOND_INTERVAL_DAYS = 6

    /** An answer at or above this quality counts as a successful recall. */
    private const val PASSING_QUALITY = 3

    private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L

    /**
     * Maps the runner's 1..9 digit onto SM-2's 0..5 quality scale.
     * 1 → 0 (nothing recalled), 9 → 5 (perfect); everything else scales linearly.
     */
    fun qualityOf(score: Score): Int = ((score.raw - 1) * 5) / 8

    /**
     * Next state for a question after one answer.
     *
     * @param previous null for a question answered for the first time.
     */
    fun next(previous: RepetitionState?, score: Score, answeredAtMs: Long): RepetitionState {
        val quality = qualityOf(score)
        val priorEase = previous?.easeFactorMilli ?: INITIAL_EASE_MILLI
        val priorRepetitions = previous?.repetitions ?: 0

        val ease = adjustEase(priorEase, quality)

        return if (quality < PASSING_QUALITY) {
            // A lapse restarts the ladder but keeps the (now lower) ease, so a question that keeps
            // being missed comes back sooner than one missed for the first time.
            RepetitionState(
                intervalDays = FIRST_INTERVAL_DAYS,
                easeFactorMilli = ease,
                repetitions = 0,
                lastAnsweredAtMs = answeredAtMs,
                nextReviewAtMs = answeredAtMs + FIRST_INTERVAL_DAYS * MILLIS_PER_DAY,
            )
        } else {
            val repetitions = priorRepetitions + 1
            val intervalDays = when (repetitions) {
                1 -> FIRST_INTERVAL_DAYS
                2 -> SECOND_INTERVAL_DAYS
                else -> {
                    val previousInterval = previous?.intervalDays ?: SECOND_INTERVAL_DAYS
                    ((previousInterval.toLong() * ease) / EASE_SCALE).toInt().coerceAtLeast(1)
                }
            }
            RepetitionState(
                intervalDays = intervalDays,
                easeFactorMilli = ease,
                repetitions = repetitions,
                lastAnsweredAtMs = answeredAtMs,
                nextReviewAtMs = answeredAtMs + intervalDays * MILLIS_PER_DAY,
            )
        }
    }

    /**
     * SM-2 ease update: EF' = EF + (0.1 - (5-q) * (0.08 + (5-q) * 0.02)), in milli-units.
     * A perfect answer nudges the ease up, a poor one drops it sharply.
     */
    private fun adjustEase(easeMilli: Int, quality: Int): Int {
        val missed = 5 - quality
        val delta = 100 - missed * (80 + missed * 20)
        return (easeMilli + delta).coerceAtLeast(MIN_EASE_MILLI)
    }
}
