package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.command

import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.PrivateQuizSettings

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
    /**
     * Настройки приватной ссылки (AD-29) — для квиза категории «Приватные квизы». `null` —
     * обычный квест. Дефолты AD-29 живут в [PrivateQuizSettings]; форма передаёт то, что
     * выбрал автор, домен хранит как есть.
     */
    val privateQuiz: PrivateQuizSettings? = null,
) {
    init {
        require(ownerUid.isNotBlank()) { "CreateQuestDraftCommand.ownerUid must not be blank" }
        require(title.isNotBlank()) { "CreateQuestDraftCommand.title must not be blank" }
        require(defaultLanguage.isNotBlank()) { "CreateQuestDraftCommand.defaultLanguage must not be blank" }
    }
}
