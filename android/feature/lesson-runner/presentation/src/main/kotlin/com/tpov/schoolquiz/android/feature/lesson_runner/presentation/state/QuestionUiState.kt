package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.state

sealed interface QuestionUiState {
    val questionText: String
    val hasImage: Boolean
    val info: String?

    data class SingleChoice(
        override val questionText: String,
        override val hasImage: Boolean,
        val imageUrl: String?,
        val options: List<OptionUi>,
        val selectedOptionId: String?,
        val correctOptionId: String? = null,
        override val info: String? = null,
    ) : QuestionUiState

    data class MultipleChoice(
        override val questionText: String,
        override val hasImage: Boolean,
        val imageUrl: String?,
        val options: List<OptionUi>,
        val selectedIds: Set<String>,
        val correctIds: Set<String> = emptySet(),
        override val info: String? = null,
    ) : QuestionUiState

    /** Survey: options with no correct answer, so nothing is ever marked right or wrong. */
    data class Survey(
        override val questionText: String,
        override val hasImage: Boolean,
        val imageUrl: String?,
        val options: List<OptionUi>,
        val selectedIds: Set<String>,
        val allowMultiple: Boolean,
        override val info: String? = null,
    ) : QuestionUiState

    data class Ordering(
        override val questionText: String,
        override val hasImage: Boolean,
        val imageUrl: String?,
        val items: List<OptionUi>,
        val correctOrderIds: List<String> = emptyList(),
        override val info: String? = null,
    ) : QuestionUiState

    data class FillBlank(
        override val questionText: String,
        override val hasImage: Boolean,
        val imageUrl: String?,
        val templateParts: List<TemplatePart>,
        val filledValues: Map<Int, String>,
        val candidates: List<OptionUi> = emptyList(),
        val correctCandidateIdsByBlankIndex: Map<Int, String> = emptyMap(),
        override val info: String? = null,
    ) : QuestionUiState
}

data class OptionUi(val id: String, val text: String)

sealed interface TemplatePart {
    data class Text(val content: String) : TemplatePart

    data class Blank(val index: Int, val placeholder: String, val blankId: String) : TemplatePart
}
