package com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate

import com.tpov.schoolquiz.android.core.designsystem.model.CatalogDisplayItem
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import com.tpov.schoolquiz.shared.feature.section.domain.model.SectionId
import com.tpov.schoolquiz.shared.feature.theme.domain.model.ThemeId

data class QuestPathItem(
    val id: QuestId,
    val title: String,
)

data class SectionPathItem(
    val id: SectionId,
    val title: String,
)

data class ThemePathItem(
    val id: ThemeId,
    val title: String,
)

data class LessonPathItem(
    val id: LessonId,
    val title: String,
)

data class QuestCreateUiState(
    val catalogs: List<CatalogDisplayItem> = emptyList(),
    val selectedCatalogId: CatalogId? = null,
    val questItems: List<QuestPathItem> = emptyList(),
    val selectedQuestId: QuestId? = null,
    val newQuestTitle: String = "",
    val sectionItems: List<SectionPathItem> = emptyList(),
    val selectedSectionId: SectionId? = null,
    val newSectionTitle: String = "",
    val themeItems: List<ThemePathItem> = emptyList(),
    val selectedThemeId: ThemeId? = null,
    val newThemeTitle: String = "",
    val lessonItems: List<LessonPathItem> = emptyList(),
    val selectedLessonId: LessonId? = null,
    val newLessonTitle: String = "",
    val availableLanguages: List<String> = listOf("ru"),
    val defaultLanguage: String = "ru",
    val defaultDifficulty: Difficulty = Difficulty.EASY,
    val activeDraftTitle: String? = null,
    val editor: QuestQuestionEditorUiState? = null,
    val isWaitingForUser: Boolean = true,
    val isCreating: Boolean = false,
    val isSubmittingToArena: Boolean = false,
    val errorMessage: UiMessage? = null,
    val arenaMessage: UiMessage? = null,
) {
    val hasActiveDraft: Boolean
        get() = activeDraftTitle != null

    val canCreate: Boolean
        get() =
            !isWaitingForUser &&
                !isCreating &&
                !isSubmittingToArena &&
                selectedCatalogId != null &&
                hasValidStructureTitles &&
                defaultLanguage.isNotBlank()

    val canContinueDraft: Boolean
        get() = !isWaitingForUser && !isCreating && !isSubmittingToArena && hasActiveDraft

    val canOpenQuestions: Boolean
        get() = !isWaitingForUser && !isCreating && !isSubmittingToArena && defaultLanguage.isNotBlank()

    val canSubmitToArena: Boolean
        get() = !isWaitingForUser && !isCreating && !isSubmittingToArena && hasActiveDraft

    private val hasValidStructureTitles: Boolean
        get() =
            (selectedQuestId != null || newQuestTitle.isNotBlank()) &&
                (selectedSectionId != null || newSectionTitle.isNotBlank()) &&
                (selectedThemeId != null || newThemeTitle.isNotBlank()) &&
                (selectedLessonId != null || newLessonTitle.isNotBlank())
}
