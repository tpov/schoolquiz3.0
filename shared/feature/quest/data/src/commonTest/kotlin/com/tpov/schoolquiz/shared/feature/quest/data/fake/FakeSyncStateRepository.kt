package com.tpov.schoolquiz.shared.feature.quest.data.fake

import com.tpov.schoolquiz.shared.core.sync.PendingCascade
import com.tpov.schoolquiz.shared.core.sync.SyncStateRepository

class FakeSyncStateRepository : SyncStateRepository {
    private val cursors = mutableMapOf<String, Long>()
    val setCursorCalls = mutableListOf<Pair<String, Long>>()

    override suspend fun getCursor(collectionId: String): Long = cursors[collectionId] ?: 0L

    override suspend fun setCursor(collectionId: String, value: Long) {
        cursors[collectionId] = value
        setCursorCalls.add(Pair(collectionId, value))
    }

    override suspend fun markCascadeInProgress(
        parentId: String,
        parentType: String,
        pendingChildIds: Set<String>,
    ) = Unit

    override suspend fun markCascadeCompleted(parentId: String, parentType: String) = Unit

    override suspend fun getPendingCascades(): List<PendingCascade> = emptyList()

    fun resetAll() {
        cursors.clear()
        setCursorCalls.clear()
    }
}
