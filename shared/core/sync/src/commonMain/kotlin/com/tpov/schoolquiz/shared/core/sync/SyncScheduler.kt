package com.tpov.schoolquiz.shared.core.sync

/**
 * How often the app syncs on its own. The player picks this in settings; whatever the choice, a
 * manual sync stays available from the menu and from settings.
 *
 * Content and the profile are scheduled separately: quests must work offline-first either way,
 * while the profile is server state that can lag behind a little.
 */
enum class SyncFrequency(
    /** Period of the background schedule; null means no recurring work. */
    val intervalMs: Long?,
) {
    /** Only when asked for — the menu item or the button in settings. */
    MANUAL(null),

    /** Once per app launch, network permitting; nothing runs in the background. */
    ON_LAUNCH(null),

    DAILY(24L * 60 * 60 * 1000),

    EVERY_3_DAYS(3L * 24 * 60 * 60 * 1000),

    WEEKLY(7L * 24 * 60 * 60 * 1000),
}

/**
 * Schedules sync work without exposing platform worker details to presentation.
 */
interface SyncScheduler {
    fun enqueueManualSync()

    /**
     * Reconciles the content schedule with [frequency]. Safe to call on every launch with the
     * stored value: implementations must make repeated calls with the same argument idempotent.
     */
    fun applyFrequency(frequency: SyncFrequency)

    /** Same contract as [applyFrequency], but for the profile's own, typically rarer cadence. */
    fun applyProfileFrequency(frequency: SyncFrequency)
}

