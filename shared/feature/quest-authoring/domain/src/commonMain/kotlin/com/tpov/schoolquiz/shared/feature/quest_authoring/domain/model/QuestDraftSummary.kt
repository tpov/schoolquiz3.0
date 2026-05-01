package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model

import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId

data class QuestDraftSummary(
    val id: QuestDraftId,
    val catalogId: CatalogId,
    val title: String,
    val status: QuestDraftStatus,
    val questionCount: Int,
    val updatedAtMs: Long,
    val isActive: Boolean,
) {
    init {
        require(title.isNotBlank()) { "QuestDraftSummary.title must not be blank" }
        require(questionCount >= 0) { "QuestDraftSummary.questionCount must be >= 0, got $questionCount" }
        require(updatedAtMs >= 0) { "QuestDraftSummary.updatedAtMs must be >= 0, got $updatedAtMs" }
    }
}
