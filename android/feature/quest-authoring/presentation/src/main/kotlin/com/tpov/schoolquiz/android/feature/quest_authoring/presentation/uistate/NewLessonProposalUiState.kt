package com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate

import com.tpov.schoolquiz.android.core.designsystem.model.CatalogDisplayItem
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftLessonId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonProposalFeeQuote
import com.tpov.schoolquiz.shared.feature.section.domain.model.SectionId
import com.tpov.schoolquiz.shared.feature.theme.domain.model.ThemeId

/** Состояние подачи правки/нового урока: ожидание → «Отправляется…» → подано/отказ. */
enum class NewLessonProposalSubmissionState {
    Idle,
    Sending,
    Done,
    Failed,
}

/**
 * Диалог «Предложить автору» (CAP-27, история 4.24): целевой путь в чужом курсе —
 * каталог → квест → раздел → тема из прочитанных опубликованных деревьев; плата 1000
 * показывается до отправки (UX-23).
 */
data class NewLessonProposalUiState(
    val visible: Boolean = false,
    val catalogs: List<CatalogDisplayItem> = emptyList(),
    val selectedCatalogId: CatalogId? = null,
    val questItems: List<QuestPathItem> = emptyList(),
    val selectedQuestId: QuestId? = null,
    val sectionItems: List<SectionPathItem> = emptyList(),
    val selectedSectionId: SectionId? = null,
    val themeItems: List<ThemePathItem> = emptyList(),
    val selectedThemeId: ThemeId? = null,
    /** Уроки черновика на выбор: предложение увозит один урок (форма NEW_LESSON 4.21). */
    val lessonChoices: List<ProposalLessonChoice> = emptyList(),
    val selectedLessonId: DraftLessonId? = null,
    val feeQuote: LessonProposalFeeQuote? = null,
    val submission: NewLessonProposalSubmissionState = NewLessonProposalSubmissionState.Idle,
) {
    val targetComplete: Boolean
        get() = selectedCatalogId != null && selectedQuestId != null &&
            selectedSectionId != null && selectedThemeId != null && selectedLessonId != null
}

/** Урок черновика в списке выбора предложения. */
data class ProposalLessonChoice(
    val id: DraftLessonId,
    val title: String,
)
