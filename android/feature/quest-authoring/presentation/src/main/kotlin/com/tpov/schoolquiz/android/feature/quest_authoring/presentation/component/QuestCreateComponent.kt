package com.tpov.schoolquiz.android.feature.quest_authoring.presentation.component

import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.FillBlankMarkerKind
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.QuestCreateUiState
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftQuestionType
import com.tpov.schoolquiz.shared.feature.section.domain.model.SectionId
import com.tpov.schoolquiz.shared.feature.theme.domain.model.ThemeId
import kotlinx.coroutines.flow.StateFlow

enum class QuestArenaTargetNode {
    QUEST,
    SECTION,
    THEME,
    LESSON,
}

@Suppress("TooManyFunctions")
interface QuestCreateComponent {
    val state: StateFlow<QuestCreateUiState>

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

    fun onStructureCheckClick()

    fun onCreateClick()

    fun onContinueDraftClick()

    fun onSubmitToArenaClick()

    fun onSubmitNodeToArenaLongClick(target: QuestArenaTargetNode)

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

    fun onQuestionPreviewClick()

    fun onBackToStructureClick()

    fun onBackClick()
}
