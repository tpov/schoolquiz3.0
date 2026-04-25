package com.tpov.schoolquiz.shared.feature.qualification.domain.dev_mode.model

/**
 * Tracks the user's tap progress toward Dev Mode activation.
 *
 * [count] — number of consecutive taps within the reset window. Range: 0..9.
 *   - 0 means no active tap sequence.
 *   - After Activated or AlreadyDev, resets back to 0.
 * [lastTapAtMillis] — epoch millis of the most recent tap, or null if no tap recorded yet.
 *
 * Invariant: count in 0..9 (count=10 is never stored — reaching 9+1 transitions to count=0).
 *
 * Note: Spec signature uses kotlinx.datetime.Instant for lastTapAt. This implementation uses
 * Long (epoch millis) because kotlinx-datetime is not yet declared as a dependency in
 * shared/feature/qualification/domain/build.gradle.kts. See Open Questions in skeleton report.
 *
 * Spec: docs/features/menu-refactor/0-spec-dev-mode.md § Terms / TapProgress
 */
data class TapProgress(
    val count: Int,
    val lastTapAtMillis: Long?,
) {
    init {
        require(count in 0..9) { "TapProgress.count must be in 0..9, was $count" }
        require(count == 0 || lastTapAtMillis != null) {
            "TapProgress.lastTapAtMillis must be non-null when count > 0, count=$count"
        }
    }

    companion object {
        /** Initial state — no tap recorded. */
        val initial: TapProgress = TapProgress(count = 0, lastTapAtMillis = null)
    }
}
