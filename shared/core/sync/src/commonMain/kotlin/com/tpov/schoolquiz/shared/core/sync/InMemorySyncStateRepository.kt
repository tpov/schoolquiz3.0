package com.tpov.schoolquiz.shared.core.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory stub for SyncStateRepository.
 */
class InMemorySyncStateRepository : SyncStateRepository {

    private val cursors = MutableStateFlow<Map<String, Long>>(emptyMap())
    private val mutex = Mutex()

    override suspend fun getCursor(collectionId: String): Long =
        cursors.value[collectionId] ?: 0L

    override suspend fun setCursor(collectionId: String, value: Long) {
        mutex.withLock {
            // maxOf guard: prevents regression if two WorkManager jobs run concurrently
            cursors.update { it + (collectionId to maxOf(it[collectionId] ?: 0L, value)) }
        }
    }

    override suspend fun resetAllCursors() {
        mutex.withLock { cursors.value = emptyMap() }
    }
}
