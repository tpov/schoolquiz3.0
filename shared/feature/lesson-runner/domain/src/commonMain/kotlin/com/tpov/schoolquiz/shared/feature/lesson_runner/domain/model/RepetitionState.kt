package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model

/**
 * Spaced-repetition schedule for one question and one person.
 *
 * @param intervalDays current gap between showings.
 * @param easeFactorMilli SM-2 ease scaled by 1000 (2500 == 2.5).
 * @param repetitions consecutive successful recalls; 0 right after a lapse.
 */
data class RepetitionState(
    val intervalDays: Int,
    val easeFactorMilli: Int,
    val repetitions: Int,
    val lastAnsweredAtMs: Long,
    val nextReviewAtMs: Long,
) {
    init {
        require(intervalDays >= 1) { "intervalDays must be at least 1" }
        require(easeFactorMilli > 0) { "easeFactorMilli must be positive" }
        require(repetitions >= 0) { "repetitions must be non-negative" }
        require(lastAnsweredAtMs >= 0) { "lastAnsweredAtMs must be non-negative" }
        require(nextReviewAtMs >= 0) { "nextReviewAtMs must be non-negative" }
    }
}
