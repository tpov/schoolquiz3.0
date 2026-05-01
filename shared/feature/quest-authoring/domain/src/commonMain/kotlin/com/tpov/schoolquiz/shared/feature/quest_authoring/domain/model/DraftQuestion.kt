package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model

import com.tpov.schoolquiz.shared.core.question_schema.Difficulty

data class DraftQuestion(
    val id: DraftQuestionId,
    val draftId: QuestDraftId,
    val lessonId: DraftLessonId,
    val type: DraftQuestionType,
    val language: String,
    val difficulty: Difficulty,
    val order: Int,
    val text: String,
    val imagePath: String?,
    val payload: String?,
    val validationState: DraftQuestionValidationState,
    val updatedAtMs: Long,
) {
    init {
        require(language.isNotBlank()) { "DraftQuestion.language must not be blank" }
        require(order >= 0) { "DraftQuestion.order must be >= 0, got $order" }
        require(imagePath == null || imagePath.isNotBlank()) {
            "DraftQuestion.imagePath must be null or non-blank"
        }
        require(payload == null || payload.isNotBlank()) {
            "DraftQuestion.payload must be null or non-blank"
        }
        require(updatedAtMs >= 0) { "DraftQuestion.updatedAtMs must be >= 0, got $updatedAtMs" }
    }
}
