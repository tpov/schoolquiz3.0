package com.tpov.schoolquiz.shared.feature.quest.data.fake

import com.tpov.schoolquiz.shared.core.sync.SyncCursor
import com.tpov.schoolquiz.shared.core.sync.SyncStateRepository

class FakeSyncStateRepository : SyncStateRepository {
    private val cursors = mutableMapOf<String, SyncCursor>()
    val setCursorCalls = mutableListOf<Pair<String, SyncCursor>>()

    override suspend fun getCursor(collectionId: String): SyncCursor = cursors[collectionId] ?: SyncCursor.BEGINNING

    override suspend fun setCursor(collectionId: String, cursor: SyncCursor) {
        cursors[collectionId] = cursor
        setCursorCalls.add(Pair(collectionId, cursor))
    }

    /** Курсор списка каталогов — одно время: списку id документа не нужен. */
    suspend fun setCursor(collectionId: String, changedAtMs: Long) = setCursor(collectionId, SyncCursor(changedAtMs))

    override suspend fun resetAllCursors() {
        cursors.clear()
    }

    fun resetAll() {
        cursors.clear()
        setCursorCalls.clear()
    }
}
