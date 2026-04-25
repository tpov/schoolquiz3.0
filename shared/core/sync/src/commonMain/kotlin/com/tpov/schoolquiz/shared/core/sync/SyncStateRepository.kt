package com.tpov.schoolquiz.shared.core.sync

/**
 * Repository for tracking sync progress across cascading entity levels.
 *
 * Phase-01 implementation: in-memory stub (InMemorySyncStateRepository).
 * Future: Room-backed implementation for atomic resume after partial fail.
 *
 * Used by SyncWorker / cascade orchestrator to:
 * - Track per-collection lastModifiedAt cursors
 * - Mark cascade progress (parent -> pending children)
 * - Recover from mid-cascade failures (future feature)
 *
 * Spec: docs/features/home-and-my-quests/0-spec.md § Sync State architecture seam
 */
interface SyncStateRepository {

    /** Returns the current cursor for the given collection. Returns 0 if never set. */
    suspend fun getCursor(collectionId: String): Long

    /** Updates the cursor for the given collection. Called after successful step completion. */
    suspend fun setCursor(collectionId: String, value: Long)

    /** Marks a cascade as in-progress for a specific parent, with its pending child ids. */
    suspend fun markCascadeInProgress(parentId: String, parentType: String, pendingChildIds: Set<String>)

    /** Marks a cascade as completed (removes from pending list). */
    suspend fun markCascadeCompleted(parentId: String, parentType: String)

    /** Returns all cascades that are still in-progress (crashed mid-way or not yet completed). */
    suspend fun getPendingCascades(): List<PendingCascade>
}

data class PendingCascade(
    val parentId: String,
    val parentType: String,
    val pendingChildIds: Set<String>,
)
