package com.tpov.schoolquiz.shared.core.sync

/**
 * Schedules sync work without exposing platform worker details to presentation.
 */
interface SyncScheduler {
    fun enqueueManualSync()
}
