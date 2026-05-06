package com.tpov.schoolquiz.android.feature.quest_authoring.presentation.component

import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.FillBlankMarkerKind
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.QuestCreateUiState
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Destination
import com.tpov.schoolquiz.shared.feature.app_shell.domain.navigation.Navigator
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftQuestionType
import com.tpov.schoolquiz.shared.feature.section.domain.model.SectionId
import com.tpov.schoolquiz.shared.feature.theme.domain.model.ThemeId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Suppress("TooManyFunctions")
class PlaceholderQuestCreateComponent(
    private val navigator: Navigator,
) : QuestCreateComponent {
    override val state: StateFlow<QuestCreateUiState> =
        MutableStateFlow(QuestCreateUiState(isWaitingForUser = false, errorMessage = "Создание квеста недоступно"))

    override fun onCatalogSelected(id: CatalogId?) = Unit

    override fun onQuestSelected(id: QuestId?) = Unit

    override fun onSectionSelected(id: SectionId?) = Unit

    override fun onThemeSelected(id: ThemeId?) = Unit

    override fun onLessonSelected(id: LessonId?) = Unit

    override fun onTitleChanged(value: String) = Unit

    override fun onDescriptionChanged(value: String) = Unit

    override fun onLanguageChanged(value: String) = Unit

    override fun onDifficultySelected(value: Difficulty) = Unit

    override fun onSectionTitleChanged(value: String) = Unit

    override fun onThemeTitleChanged(value: String) = Unit

    override fun onLessonTitleChanged(value: String) = Unit

    override fun onStructureCheckClick() = Unit

    override fun onCreateClick() = Unit

    override fun onContinueDraftClick() = Unit

    override fun onSubmitToArenaClick() = Unit

    override fun onSubmitNodeToArenaLongClick(target: QuestArenaTargetNode) = Unit

    override fun onQuestionsClick(difficulty: Difficulty) = Unit

    override fun onQuestionSelected(index: Int) = Unit

    override fun onPreviousQuestionClick() = Unit

    override fun onNextQuestionClick() = Unit

    override fun onAddQuestionClick() = Unit

    override fun onQuestionTypeSelected(value: DraftQuestionType) = Unit

    override fun onQuestionDifficultySelected(value: Difficulty) = Unit

    override fun onQuestionLanguageChanged(value: String) = Unit

    override fun onQuestionTextChanged(value: String) = Unit

    override fun onQuestionImagePathChanged(value: String) = Unit

    override fun onQuestionInfoChanged(value: String) = Unit

    override fun onOptionTextChanged(
        index: Int,
        value: String,
    ) = Unit

    override fun onOptionAdded() = Unit

    override fun onOptionRemoved(index: Int) = Unit

    override fun onSingleCorrectSelected(index: Int) = Unit

    override fun onMultipleCorrectToggled(index: Int) = Unit

    override fun onOrderingItemTextChanged(
        index: Int,
        value: String,
    ) = Unit

    override fun onOrderingItemAdded() = Unit

    override fun onOrderingItemRemoved(index: Int) = Unit

    override fun onFillBlankTextChanged(value: String) = Unit

    override fun onFillBlankMarkerAdded(kind: FillBlankMarkerKind) = Unit

    override fun onFillBlankAnswerChanged(
        index: Int,
        value: String,
    ) = Unit

    override fun onFillBlankAnswerProtectedChanged(
        index: Int,
        value: Boolean,
    ) = Unit

    override fun onFillBlankAnswerAdded() = Unit

    override fun onFillBlankAnswerRemoved(index: Int) = Unit

    override fun onFillBlankDistractorChanged(
        index: Int,
        value: String,
    ) = Unit

    override fun onFillBlankDistractorAdded() = Unit

    override fun onFillBlankDistractorRemoved(index: Int) = Unit

    override fun onSaveQuestionClick() = Unit

    override fun onQuestionPreviewClick() = Unit

    override fun onBackToStructureClick() = Unit

    override fun onBackClick() = navigator.goTo(Destination.Back)
}
