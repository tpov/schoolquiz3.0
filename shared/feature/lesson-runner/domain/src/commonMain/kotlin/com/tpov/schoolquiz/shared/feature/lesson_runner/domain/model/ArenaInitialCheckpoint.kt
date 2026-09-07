package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model

/** User-intent-time runner parameters. No arbitrary JSON or regenerated attempt identity. */
data class ArenaInitialCheckpoint(
    val sessionMode: SessionMode,
    val seed: Long,
    val questionStartedAtMs: Long,
    val deadlineMs: Long,
) {
    init {
        require(questionStartedAtMs >= 0 && deadlineMs >= questionStartedAtMs)
    }
}
