package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model

data class DraftTheme(
    val id: DraftThemeId,
    val draftId: QuestDraftId,
    val sectionId: DraftSectionId,
    val title: String,
    val order: Int,
) {
    init {
        require(order >= 0) { "DraftTheme.order must be >= 0, got $order" }
    }
}
