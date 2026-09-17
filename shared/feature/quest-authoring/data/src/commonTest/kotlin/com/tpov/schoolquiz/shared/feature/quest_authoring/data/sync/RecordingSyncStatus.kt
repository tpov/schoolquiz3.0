package com.tpov.schoolquiz.shared.feature.quest_authoring.data.sync

import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.sync.SyncRunKind
import com.tpov.schoolquiz.shared.core.sync.SyncStatusRepository

/** Куда журналы кладут счёт непонятых записей — по журналу, чтобы они не затирали друг друга. */
internal class RecordingSyncStatus : SyncStatusRepository {
    val byJournal = mutableMapOf<String, Int>()

    override fun observeStatus() = throw UnsupportedOperationException("не нужен этому тесту")

    override suspend fun recordSuccess(kind: SyncRunKind, atMs: Long) = Unit

    override suspend fun recordFailure(kind: SyncRunKind, error: SyncError, atMs: Long) = Unit

    override suspend fun recordUnreadableChanges(journal: String, count: Int) {
        byJournal[journal] = count
    }
}
