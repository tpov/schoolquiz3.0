package com.tpov.schoolquiz.shared.core.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory stub for SyncStateRepository.
 *
 * Phase-01 implementation — state lost on process kill. This is acceptable
 * for MVP because sync is idempotent via upsert-by-id; next run will re-pull
 * via cursor (starting from 0 if this stub was used previously).
 *
 * Future: replaced by RoomSyncStateRepository for persistent cascade tracking
 * + atomic resume. Domain interface does not change.
 */
class InMemorySyncStateRepository : SyncStateRepository {

    private val cursors = MutableStateFlow<Map<String, Long>>(emptyMap())
    private val pending = MutableStateFlow<Map<Pair<String, String>, PendingCascade>>(emptyMap())
    private val mutex = Mutex()

    override suspend fun getCursor(collectionId: String): Long =
        cursors.value[collectionId] ?: 0L

    override suspend fun setCursor(collectionId: String, value: Long) {
        mutex.withLock {
            // maxOf guard: prevents regression if two WorkManager jobs run concurrently
            cursors.update { it + (collectionId to maxOf(it[collectionId] ?: 0L, value)) }
        }
    }

    override suspend fun markCascadeInProgress(
        parentId: String,
        parentType: String,
        pendingChildIds: Set<String>,
    ) {
        mutex.withLock {
            pending.update {
                it + ((parentId to parentType) to PendingCascade(parentId, parentType, pendingChildIds))
            }
        }
    }

    override suspend fun markCascadeCompleted(parentId: String, parentType: String) {
        mutex.withLock {
            pending.update { it - (parentId to parentType) }
        }
    }

    override suspend fun getPendingCascades(): List<PendingCascade> =
        pending.value.values.toList()
}
