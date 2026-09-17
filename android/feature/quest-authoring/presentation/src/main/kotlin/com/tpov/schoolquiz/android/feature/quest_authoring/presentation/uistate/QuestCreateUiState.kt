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

/**
 * Черновик разошёлся с сервером: заявка ушла с одной версией, а квест уже другой (AD-24).
 *
 * Не отказ и не потеря: работа автора лежит в очереди и ждёт одного решения — отправить её заново
 * поверх той версии, что на сервере сейчас.
 *
 * @property serverVersion версия на сервере. Пусто — сервер назвал расхождение, но версию не
 *   назвал, и тогда решать автору нечего: отправка заново ушла бы вообще без сверки (пустая
 *   ожидаемая версия — пропуск проверки на приёмнике), то есть слепо поверх неизвестно чего.
 *   Такое расхождение снимается следующим снимком с сервера, а не кнопкой.
 * @property expectedVersion версия, с которой заявка уходила. Для объяснения, не для решения.
 */
data class DraftConflictUiState(
    val serverVersion: Long?,
    val expectedVersion: Long?,
) {
    /** Названа ли сервером версия, поверх которой есть что отправлять. */
    val hasServerVersion: Boolean
        get() = serverVersion != null
}

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
    val draftConflict: DraftConflictUiState? = null,
) {
    /**
     * Показывать ли действие «отправить заново поверх версии N».
     *
     * Появляется только при живой конфликтной записи в очереди: нет расхождения — нет и решения,
     * которое автору предлагать (AD-24). И только при названной сервером версии: без номера
     * отправлять было бы не «поверх версии N», а вслепую и без сверки — прежний экран показывал
     * на этом месте «поверх версии 0», числа, которого не существует.
     */
    val canResubmitOverServerVersion: Boolean
        get() =
            draftConflict?.hasServerVersion == true &&
                !isSubmittingToArena &&
                !isWaitingForUser

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
