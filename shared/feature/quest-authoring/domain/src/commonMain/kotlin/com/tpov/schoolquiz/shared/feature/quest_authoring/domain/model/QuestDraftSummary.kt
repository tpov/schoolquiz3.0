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
    /**
     * Почему заявку вернули автору, если её вернули.
     *
     * Хранится с самого начала и доезжает до [QuestDraft], но до списка «Мои квесты» не доходила
     * — автор видел «Черновик» и не знал, отклонили его работу или он сам её не дослал.
     */
    val rejectionReason: String? = null,
) {
    init {
        require(title.isNotBlank()) { "QuestDraftSummary.title must not be blank" }
        require(questionCount >= 0) { "QuestDraftSummary.questionCount must be >= 0, got $questionCount" }
        require(updatedAtMs >= 0) { "QuestDraftSummary.updatedAtMs must be >= 0, got $updatedAtMs" }
    }
}
