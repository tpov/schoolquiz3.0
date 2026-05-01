package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model

data class DraftSection(
    val id: DraftSectionId,
    val draftId: QuestDraftId,
    val title: String,
    val order: Int,
) {
    init {
        require(order >= 0) { "DraftSection.order must be >= 0, got $order" }
    }
}
