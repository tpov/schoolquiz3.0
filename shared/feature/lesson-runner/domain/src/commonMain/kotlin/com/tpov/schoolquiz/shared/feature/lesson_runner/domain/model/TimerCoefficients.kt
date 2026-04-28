package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model

/**
 * Config constants for per-question timer formula.
 * seconds = max(5, round(charsCount × k))
 * +100 chars equivalent added when question has an image.
 */
data class TimerCoefficients(
    val kEasy: Double,
    val kHard: Double,
) {
    companion object {
        val Default = TimerCoefficients(kEasy = 0.36, kHard = 0.24)
    }
}
