package com.tpov.schoolquiz.android.feature.quest_authoring.presentation.component

import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.FillBlankMarkerKind
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.NewLessonProposalUiState
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.QuestArenaTargetNode
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.QuestCreateUiState
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftLessonId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftQuestionType
import com.tpov.schoolquiz.shared.feature.section.domain.model.SectionId
import com.tpov.schoolquiz.shared.feature.theme.domain.model.ThemeId
import kotlinx.coroutines.flow.StateFlow

@Suppress("TooManyFunctions")
interface QuestCreateComponent {
    val state: StateFlow<QuestCreateUiState>

    /**
     * «Предложить автору» (CAP-27, история 4.24): целевой путь в чужом курсе для урока
     * черновика. Пикеры читают опубликованные деревья, плата 1000 видна до отправки (UX-23).
     */
    val newLessonProposalState: StateFlow<NewLessonProposalUiState>

    fun onNewProposalDialogOpened()

    fun onNewProposalDialogDismissed()

    fun onProposalTargetCatalogSelected(id: CatalogId?)

    fun onProposalTargetQuestSelected(id: QuestId?)

    fun onProposalTargetSectionSelected(id: SectionId?)

    fun onProposalTargetThemeSelected(id: ThemeId?)

    fun onProposalLessonSelected(id: DraftLessonId?)

    fun onProposeLessonToAuthorClick()

    fun onCatalogSelected(id: CatalogId?)
    
    fun onQuestSelected(id: QuestId?)

    fun onSectionSelected(id: SectionId?)

    fun onThemeSelected(id: ThemeId?)

    fun onLessonSelected(id: LessonId?)

    fun onTitleChanged(value: String)

    fun onDescriptionChanged(value: String)

    fun onLanguageChanged(value: String)

    fun onDifficultySelected(value: Difficulty)

    fun onSectionTitleChanged(value: String)

    fun onThemeTitleChanged(value: String)

    fun onLessonTitleChanged(value: String)

    // ─── Приватная категория (AD-29): настройки ссылки и ручное закрытие ────────────────────────
    fun onPrivateAttemptLimitChanged(value: Int)

    fun onPrivateShowResultChanged(value: Boolean)

    fun onPrivateLinkExpiryToggled(hasExpiry: Boolean)

    fun onPrivateLinkExpiryDaysChanged(days: Int)

    fun onClosePrivateQuizLinkClick()

    fun onStructureCheckClick()

    fun onCreateClick()

    fun onContinueDraftClick()

    fun onSubmitToArenaClick()

    fun onSubmitNodeToArenaLongClick(target: QuestArenaTargetNode)

    /**
     * Решение автора по расхождению: принять серверную версию и отправить работу заново (AD-24).
     *
     * Не повтор прежней заявки — новая мутация с новым ключом, а прежняя запись снимается: тот
     * ключ сервер уже видел и отверг (AD-2).
     */
    fun onResubmitOverServerVersionClick()

    fun onQuestionsClick(difficulty: Difficulty)

    fun onQuestionSelected(index: Int)

    fun onPreviousQuestionClick()

    fun onNextQuestionClick()

    fun onAddQuestionClick()

    fun onQuestionTypeSelected(value: DraftQuestionType)

    fun onQuestionDifficultySelected(value: Difficulty)

    fun onQuestionLanguageChanged(value: String)

    fun onQuestionTextChanged(value: String)

    fun onQuestionImagePathChanged(value: String)

    fun onQuestionInfoChanged(value: String)

    fun onOptionTextChanged(
        index: Int,
        value: String,
    )

    fun onOptionAdded()

    fun onOptionRemoved(index: Int)

    fun onSingleCorrectSelected(index: Int)

    fun onMultipleCorrectToggled(index: Int)

    fun onOrderingItemTextChanged(
        index: Int,
        value: String,
    )

    fun onOrderingItemAdded()

    fun onOrderingItemRemoved(index: Int)

    fun onFillBlankTextChanged(value: String)

    fun onFillBlankMarkerAdded(kind: FillBlankMarkerKind)

    fun onFillBlankAnswerChanged(
        index: Int,
        value: String,
    )

    fun onFillBlankAnswerProtectedChanged(
        index: Int,
        value: Boolean,
    )

    fun onFillBlankAnswerAdded()

    fun onFillBlankAnswerRemoved(index: Int)

    fun onFillBlankDistractorChanged(
        index: Int,
        value: String,
    )

    fun onFillBlankDistractorAdded()

    fun onFillBlankDistractorRemoved(index: Int)

    fun onSaveQuestionClick()

    /**
     * Отменить последнюю содержательную правку вопроса (UX-O6 §4): текст и его метки
     * возвращаются вместе, одна правка — один шаг истории.
     */
    fun onEditorUndoClick()

    /** Вернуть правку, отменённую последним undo. */
    fun onEditorRedoClick()

    fun onQuestionPreviewClick()

    fun onBackToStructureClick()

    fun onBackClick()
}
