package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model

import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId

data class QuestDraft(
    val id: QuestDraftId,
    val ownerUid: String,
    val catalogId: CatalogId,
    val title: String,
    val description: String?,
    val defaultLanguage: String,
    val defaultDifficulty: Difficulty,
    val status: QuestDraftStatus,
    val localRevision: Long,
    val serverRevision: Long?,
    val publicQuestId: QuestId?,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    val isActive: Boolean,
) {
    init {
        require(ownerUid.isNotBlank()) { "QuestDraft.ownerUid must not be blank" }
        require(title.isNotBlank()) { "QuestDraft.title must not be blank" }
        require(defaultLanguage.isNotBlank()) { "QuestDraft.defaultLanguage must not be blank" }
        require(localRevision >= 1) { "QuestDraft.localRevision must be >= 1, got $localRevision" }
        require(serverRevision == null || serverRevision >= 1) {
            "QuestDraft.serverRevision must be null or >= 1, got $serverRevision"
        }
        require(createdAtMs >= 0) { "QuestDraft.createdAtMs must be >= 0, got $createdAtMs" }
        require(updatedAtMs >= createdAtMs) {
            "QuestDraft.updatedAtMs must be >= createdAtMs, got $updatedAtMs < $createdAtMs"
        }
    }
}
