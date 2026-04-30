package com.tpov.schoolquiz.shared.core.sync

/**
 * Repository for tracking sync progress.
 *
 * Production implementation stores cursors in Room. The sync-list orchestrator
 * keeps one cursor for the catalog list and one cursor per catalog change list.
 */
interface SyncStateRepository {

    /** Returns the current cursor for the given collection. Returns 0 if never set. */
    suspend fun getCursor(collectionId: String): Long

    /** Updates the cursor for the given collection. Called after successful step completion. */
    suspend fun setCursor(collectionId: String, value: Long)
}
