package com.tpov.schoolquiz.shared.feature.qualification.domain.dev_mode.model

/**
 * Result of processing a single tap in the Dev Mode unlock sequence.
 *
 * All variants carry [newProgress] — the updated [TapProgress] after the tap.
 * The caller (use case or ViewModel) stores [newProgress] as the next state.
 *
 * Spec: docs/features/menu-refactor/0-spec-dev-mode.md § Terms / TapResult
 * Codex fix #4: all variants carry newProgress; post-Activated/AlreadyDev state is (0, null).
 *
 * - [NoChange]: tap registered, count incremented. Not at targetCount yet.
 * - [Activated]: tap reached targetCount AND developer <= LEVEL_1.points. Dev Mode unlocked.
 * - [AlreadyDev]: tap reached targetCount BUT developer > LEVEL_1.points. Guard blocked write.
 * - [Reset]: tap arrived after timeout (elapsed > resetThresholdMillis). Count reset to 1.
 */
sealed interface TapResult {

    val newProgress: TapProgress

    /** Tap registered. Sequence continues. [newProgress.count] = previous count + 1. */
    data class NoChange(override val newProgress: TapProgress) : TapResult

    /**
     * 10th tap within timeout AND developer <= LEVEL_1.points.
     * Dev Mode is now active. [newProgress] = TapProgress(0, null) — reset for future taps.
     */
    data class Activated(override val newProgress: TapProgress) : TapResult

    /**
     * 10th tap within timeout BUT developer > LEVEL_1.points (already a developer).
     * No write to overlay. [newProgress] = TapProgress(0, null) — reset.
     */
    data class AlreadyDev(override val newProgress: TapProgress) : TapResult

    /**
     * Tap arrived after resetThresholdMillis pause. Sequence restarted.
     * [newProgress.count] = 1 (this tap counts as the first of a new sequence).
     */
    data class Reset(override val newProgress: TapProgress) : TapResult
}
