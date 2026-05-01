package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.command

import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId

data class CreateQuestDraftCommand(
    val ownerUid: String,
    val catalogId: CatalogId,
    val sourceQuestId: QuestId?,
    val title: String,
    val description: String?,
    val defaultLanguage: String,
    val defaultDifficulty: Difficulty,
    val sectionTitle: String,
    val themeTitle: String,
    val lessonTitle: String,
) {
    init {
        require(ownerUid.isNotBlank()) { "CreateQuestDraftCommand.ownerUid must not be blank" }
        require(title.isNotBlank()) { "CreateQuestDraftCommand.title must not be blank" }
        require(defaultLanguage.isNotBlank()) { "CreateQuestDraftCommand.defaultLanguage must not be blank" }
    }
}
