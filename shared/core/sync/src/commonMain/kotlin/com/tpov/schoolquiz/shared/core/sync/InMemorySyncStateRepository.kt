package com.tpov.schoolquiz.shared.core.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory stub for SyncStateRepository.
 */
class InMemorySyncStateRepository : SyncStateRepository {
    private val cursors = MutableStateFlow<Map<String, SyncCursor>>(emptyMap())
    private val mutex = Mutex()

    override suspend fun getCursor(collectionId: String): SyncCursor =
        cursors.value[collectionId] ?: SyncCursor.BEGINNING

    override suspend fun setCursor(
        collectionId: String,
        cursor: SyncCursor,
    ) {
        mutex.withLock {
            // Монотонность по паре, а не по числу: два наложившихся прохода иначе откатили бы
            // курсор друг друга.
            cursors.update { it + (collectionId to maxOf(it[collectionId] ?: SyncCursor.BEGINNING, cursor)) }
        }
    }

    override suspend fun resetAllCursors() {
        mutex.withLock { cursors.value = emptyMap() }
    }
}
