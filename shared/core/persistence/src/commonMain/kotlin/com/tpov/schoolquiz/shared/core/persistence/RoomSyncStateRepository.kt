package com.tpov.schoolquiz.shared.core.persistence

import com.tpov.schoolquiz.shared.core.sync.SyncCursor
import com.tpov.schoolquiz.shared.core.sync.SyncStateRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class RoomSyncStateRepository(
    private val dao: SyncStateDao,
) : SyncStateRepository {
    private val mutex = Mutex()

    override suspend fun getCursor(collectionId: String): SyncCursor =
        dao.find(collectionId)?.toCursor() ?: SyncCursor.BEGINNING

    override suspend fun setCursor(
        collectionId: String,
        cursor: SyncCursor,
    ) {
        mutex.withLock {
            dao.setCursor(collectionId, cursor)
        }
    }

    override suspend fun resetAllCursors() {
        mutex.withLock {
            dao.clearAllCursors()
        }
    }
}
