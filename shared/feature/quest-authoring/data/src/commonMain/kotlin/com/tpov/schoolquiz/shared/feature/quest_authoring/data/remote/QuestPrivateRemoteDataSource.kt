package com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote

data class PrivateQuestSnapshot(
    val serverRevision: Long,
    val changedAtMs: Long,
    val request: QuestArenaSubmissionRequest,
) {
    init {
        require(serverRevision >= 0L) { "PrivateQuestSnapshot.serverRevision must be >= 0" }
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
    suspend fun fetchChangedSince(cursorMs: Long): List<PrivateQuestSyncChange>

    suspend fun fetchSnapshots(changes: List<PrivateQuestSyncChange>): List<PrivateQuestSnapshot>
}
