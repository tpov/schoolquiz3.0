package com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate

import com.tpov.schoolquiz.android.core.designsystem.model.CatalogDisplayItem
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.PrivateQuizSettings
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
    // ─── Приватная категория (AD-29): настройки ссылки и управление ею ─────────────────────────
    /** Целый лимит попыток, ≥ 1. Дефолт AD-29: одна попытка. */
    val privateAttemptLimit: Int = PrivateQuizSettings.DEFAULT_ATTEMPT_LIMIT,
    /** Видимость результата участнику. Дефолт AD-29: скрыт. */
    val privateShowResult: Boolean = PrivateQuizSettings.DEFAULT_SHOW_RESULT,
    /** Срок ссылки задан. Дефолт AD-29: «без срока». */
    val privateLinkHasExpiry: Boolean = false,
    /** Момент, до которого старт разрешён; значим только при [privateLinkHasExpiry]. */
    val privateLinkExpiresInDays: Int = DEFAULT_LINK_EXPIRY_DAYS,
    /** Ручное закрытие ссылки поставлено в очередь; ссылка закрывается навсегда (re-enable не назначен). */
    val isLinkCloseQueued: Boolean = false,
) {
    /**
     * Выбранная категория — «Приватные квизы» (AD-29/UX-15).
     *
     * Категория одна и засеяна техническим идентификатором; questType у неё REGULAR (приватный
     * квиз — оцениваемый HARD-набор, а не опрос), поэтому отличить её можно только по нему.
     */
    val isPrivateCategory: Boolean
        get() = selectedCatalogId?.value == PrivateQuizSettings.PRIVATE_QUIZ_CATALOG_ID

    val canClosePrivateLink: Boolean
        get() = isPrivateCategory && hasActiveDraft && !isLinkCloseQueued && !isSubmittingToArena
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

    /**
     * Структура черновика достаточна для сохранения.
     *
     * Приватная категория (AD-29) — плоский HARD-набор: иерархии автору нет, раздел/тема/урок
     * создаются серверным конвейером под капотом, и от автора требуется только название квиза.
     */
    private val hasValidStructureTitles: Boolean
        get() =
            (selectedQuestId != null || newQuestTitle.isNotBlank()) &&
                (
                    isPrivateCategory ||
                        (
                            (selectedSectionId != null || newSectionTitle.isNotBlank()) &&
                                (selectedThemeId != null || newThemeTitle.isNotBlank()) &&
                                (selectedLessonId != null || newLessonTitle.isNotBlank())
                        )
                )
}

/** Пресеты срока ссылки; «без срока» живёт отдельным переключателем, не пресетом. */
const val MILLIS_PER_DAY: Long = 24L * 60 * 60 * 1000
const val DEFAULT_LINK_EXPIRY_DAYS: Int = 1
val LINK_EXPIRY_DAY_PRESETS: List<Int> = listOf(1, 7, 30)
