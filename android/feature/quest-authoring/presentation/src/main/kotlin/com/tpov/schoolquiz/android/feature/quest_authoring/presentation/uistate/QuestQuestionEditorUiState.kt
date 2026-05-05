package com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate

import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.logic.FillBlankAnswerSpec
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.logic.extractFillBlankAnswers
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.logic.orderFillBlankAnswersByText
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftLessonId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftQuestionId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftQuestionType
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraftId

private const val MIN_OPTIONS = 2
private const val MIN_MULTIPLE_CORRECT = 2
private const val MIN_FILL_BLANK_ANSWERS = 1
private const val MAX_FILL_BLANK_ANSWERS = 3

data class FillBlankAnswerItem(
    val text: String = "",
    val isProtected: Boolean = false,
)

enum class FillBlankMarkerKind {
    BLANK,
    PROTECTED,
    BLANK_PROTECTED,
}

data class QuestQuestionEditorUiState(
    val draftId: QuestDraftId,
    val draftTitle: String,
    val lessonId: DraftLessonId,
    val questionItems: List<DraftQuestionListItem> = emptyList(),
    val activeIndex: Int = 0,
    val selectedQuestionId: DraftQuestionId? = null,
    val type: DraftQuestionType = DraftQuestionType.SINGLE_CHOICE,
    val difficulty: Difficulty = Difficulty.EASY,
    val language: String = "ru",
    val text: String = "",
    val imagePath: String = "",
    val info: String = "",
    val optionTexts: List<String> = listOf("", ""),
    val correctSingleIndex: Int = 0,
    val correctMultipleIndexes: Set<Int> = emptySet(),
    val orderingItems: List<String> = listOf("", ""),
    val fillBlankText: String = "",
    val fillBlankAnswers: List<FillBlankAnswerItem> = listOf(FillBlankAnswerItem()),
    val fillBlankDistractors: List<String> = listOf("", "", "", ""),
    val isSaving: Boolean = false,
    val isPreviewVisible: Boolean = false,
    val errorMessage: String? = null,
    val lastSavedMessage: String? = null,
) {
    val displayQuestionNumber: Int
        get() = activeIndex + 1

    val totalSlots: Int
        get() = maxOf(questionItems.size, activeIndex + 1)

    val canGoPrevious: Boolean
        get() = activeIndex > 0

    val canGoNext: Boolean
        get() = activeIndex < questionItems.lastIndex

    val isNewQuestion: Boolean
        get() = selectedQuestionId == null

    val canSave: Boolean
        get() =
            !isSaving &&
                when (type) {
                    DraftQuestionType.SINGLE_CHOICE ->
                        text.isNotBlank() &&
                            nonBlankOptionIndexes().size >= MIN_OPTIONS &&
                            correctSingleIndex in nonBlankOptionIndexes()
                    DraftQuestionType.MULTIPLE_CHOICE ->
                        text.isNotBlank() &&
                            nonBlankOptionIndexes().size >= MIN_OPTIONS &&
                            correctMultipleIndexes.count { it in nonBlankOptionIndexes() } >= MIN_MULTIPLE_CORRECT
                    DraftQuestionType.ORDERING ->
                        text.isNotBlank() &&
                            orderingItems.count { it.isNotBlank() } >= MIN_OPTIONS
                    DraftQuestionType.FILL_BLANK ->
                        fillBlankCanSave()
                }

    private fun nonBlankOptionIndexes(): Set<Int> =
        optionTexts
            .mapIndexedNotNull { index, value -> index.takeIf { value.isNotBlank() } }
            .toSet()

    private fun fillBlankCanSave(): Boolean {
        val answers = orderedFillBlankAnswers() ?: return false
        return fillBlankText.isNotBlank() &&
            answers.size in MIN_FILL_BLANK_ANSWERS..MAX_FILL_BLANK_ANSWERS &&
            answers.map { it.text.lowercase() }.toSet().size == answers.size
    }

    private fun orderedFillBlankAnswers(): List<FillBlankAnswerSpec>? {
        val markupAnswers = extractFillBlankAnswers(fillBlankText)
        if (markupAnswers.isNotEmpty()) return markupAnswers

        val manualAnswers =
            fillBlankAnswers.mapNotNull { answer ->
                answer.text.trim().takeIf { it.isNotEmpty() }?.let { text ->
                    FillBlankAnswerSpec(
                        text = text,
                        isProtected = answer.isProtected,
                    )
                }
            }
        return orderFillBlankAnswersByText(
            text = fillBlankText,
            answers = manualAnswers,
        )
    }
}

data class DraftQuestionListItem(
    val id: DraftQuestionId,
    val number: Int,
    val title: String,
    val type: DraftQuestionType,
    val difficulty: Difficulty,
)
