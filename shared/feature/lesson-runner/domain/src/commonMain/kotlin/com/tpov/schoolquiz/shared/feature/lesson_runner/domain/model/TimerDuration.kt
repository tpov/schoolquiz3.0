package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model

/**
 * Per-question timer in seconds. Minimum floor is 5 seconds.
 * Derived from question content character count × mode coefficient.
 */
@JvmInline
value class TimerDuration(val seconds: Int) {
    init {
        require(seconds >= 5) { "TimerDuration.seconds must be >= 5, got $seconds" }
    }
}
