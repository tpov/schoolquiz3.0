package com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote

import com.tpov.schoolquiz.shared.core.sync.SYNC_PAGE_SIZE
import com.tpov.schoolquiz.shared.core.sync.SyncChangePage
import com.tpov.schoolquiz.shared.core.sync.SyncCursor

/**
 * Приватный квест, приехавший с сервера.
 *
 * @property serverRevision версия, назначенная сервером (AD-12). `null` — сервер этому квесту
 *   версии ещё не назначал: сверять не с чем, и подставлять сюда местное число нельзя — сравнение
 *   клиента с самим собой даёт расхождение на ровном месте.
 */
data class PrivateQuestSnapshot(
    val serverRevision: Long?,
    val changedAtMs: Long,
    val request: QuestArenaSubmissionRequest,
) {
    init {
        require(serverRevision == null || serverRevision >= 1L) {
            "PrivateQuestSnapshot.serverRevision must be null or >= 1, got $serverRevision"
        }
        require(changedAtMs >= 0L) { "PrivateQuestSnapshot.changedAtMs must be >= 0" }
    }
}

data class PrivateQuestSyncChange(
    val catalogId: String,
    val questId: String,
    val changedAtMs: Long,
) {
    init {
        require(catalogId.isNotBlank()) { "PrivateQuestSyncChange.catalogId must not be blank" }
        require(questId.isNotBlank()) { "PrivateQuestSyncChange.questId must not be blank" }
        require(changedAtMs >= 0L) { "PrivateQuestSyncChange.changedAtMs must be >= 0" }
    }
}

interface QuestPrivateRemoteDataSource {
    /**
     * Одна страница журнала `private/{uid}/sync_changes`, начиная сразу после [cursor] (AD-31).
     *
     * Продолжение — по паре «время, id документа»: две записи в одну миллисекунду иначе
     * сливались бы. Непонятые записи считаются в [SyncChangePage.unreadable], а не пропадают.
     */
    suspend fun fetchPage(
        cursor: SyncCursor,
        limit: Int = SYNC_PAGE_SIZE,
    ): SyncChangePage<PrivateQuestSyncChange>

    suspend fun fetchSnapshots(changes: List<PrivateQuestSyncChange>): List<PrivateQuestSnapshot>
}
