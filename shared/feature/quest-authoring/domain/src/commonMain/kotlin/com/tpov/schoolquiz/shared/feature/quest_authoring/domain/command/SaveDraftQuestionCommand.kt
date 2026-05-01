package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.command

import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftLessonId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftQuestionId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftQuestionType
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraftId

data class SaveDraftQuestionCommand(
    val draftId: QuestDraftId,
    val lessonId: DraftLessonId,
    val questionId: DraftQuestionId?,
    val type: DraftQuestionType,
    val language: String,
    val difficulty: Difficulty,
    val order: Int,
    val imagePath: String?,
    val payload: String,
) {
    init {
        require(language.isNotBlank()) { "SaveDraftQuestionCommand.language must not be blank" }
        require(order >= 0) { "SaveDraftQuestionCommand.order must be >= 0, got $order" }
        require(imagePath == null || imagePath.isNotBlank()) {
            "SaveDraftQuestionCommand.imagePath must be null or non-blank"
        }
        require(payload.isNotBlank()) { "SaveDraftQuestionCommand.payload must not be blank" }
    }
}
