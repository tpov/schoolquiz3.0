package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model

/**
 * Derived UI value for star display. Integer tenths [0..30] (3 stars × 10 parts).
 * UI renders as rawTenths / 10f. Not persisted in Attempt.
 */
@JvmInline
value class Stars(val rawTenths: Int) {
    init {
        require(rawTenths in 0..30) { "Stars.rawTenths must be in 0..30, got $rawTenths" }
    }
}
