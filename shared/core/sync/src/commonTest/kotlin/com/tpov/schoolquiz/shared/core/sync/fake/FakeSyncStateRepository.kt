package com.tpov.schoolquiz.shared.core.sync.fake

import com.tpov.schoolquiz.shared.core.sync.SyncStateRepository

class FakeSyncStateRepository : SyncStateRepository {
    private val cursors = mutableMapOf<String, Long>()
    val setCursorCalls = mutableListOf<Pair<String, Long>>()

    override suspend fun getCursor(collectionId: String): Long = cursors[collectionId] ?: 0L

    override suspend fun setCursor(collectionId: String, value: Long) {
        cursors[collectionId] = value
        setCursorCalls.add(Pair(collectionId, value))
    }

    override suspend fun resetAllCursors() {
        cursors.clear()
    }

    fun resetAll() {
        cursors.clear()
        setCursorCalls.clear()
    }
}
