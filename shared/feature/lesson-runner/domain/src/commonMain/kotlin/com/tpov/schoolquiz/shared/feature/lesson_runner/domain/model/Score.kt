package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model

/**
 * Per-question score digit: 0 = not shown; 1..9 = shown with correctness share.
 * Formula: digit = round(correct_share × 8) + 1 for shown questions.
 */
@JvmInline
value class Score(val raw: Int) {
    init {
        require(raw in 0..9) { "Score.raw must be in 0..9, got $raw" }
    }
}
