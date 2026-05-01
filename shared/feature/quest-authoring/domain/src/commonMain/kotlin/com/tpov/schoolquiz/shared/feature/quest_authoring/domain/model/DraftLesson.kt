package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model

data class DraftLesson(
    val id: DraftLessonId,
    val draftId: QuestDraftId,
    val themeId: DraftThemeId,
    val title: String,
    val order: Int,
) {
    init {
        require(order >= 0) { "DraftLesson.order must be >= 0, got $order" }
    }
}
