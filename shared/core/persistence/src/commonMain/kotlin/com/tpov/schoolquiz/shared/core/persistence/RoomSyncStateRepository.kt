package com.tpov.schoolquiz.shared.core.persistence

import com.tpov.schoolquiz.shared.core.sync.SyncStateRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class RoomSyncStateRepository(
    private val dao: SyncStateDao,
) : SyncStateRepository {

    private val mutex = Mutex()

    override suspend fun getCursor(collectionId: String): Long =
        dao.getCursor(collectionId) ?: 0L

    override suspend fun setCursor(collectionId: String, value: Long) {
        mutex.withLock {
            dao.setCursor(collectionId, value)
        }
    }
}
