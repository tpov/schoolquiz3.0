package com.tpov.schoolquiz.presentation.create.model

import android.net.Uri
import com.tpov.common.data.model.local.QuestionLocal

data class QuizUiModelState(
    val quizNameUiState: TextUiState? = null,
    val quizImageUiState: ImageUiState? = null,

    val categorySpinnerUiState: SpinnerUiState? = null,
    val subCategorySpinnerUiState: SpinnerUiState? = null,
    val subsubCategorySpinnerUiState: SpinnerUiState? = null,

    val llCreateNewCategory: isUiState? = null,
    val tvCategory: TextUiState? = null,
    val imvCategory: ImageUiState? = null,
    val tvSubCategory: TextUiState? = null,
    val imvSubCategory: ImageUiState? = null,
    val tvSubsubCategory: TextUiState? = null,
    val imvSubsubCategory: ImageUiState? = null,

    val stroceTop: isUiState? = null,
    val questionImageUiState: ImageUiState? = null,
    val fullscreenButtonUiState: CheckBoxUiState? = null,
    val fullScreen: isUiState? = null,
    val questionNumberSpinnerUiState: SpinnerUiState? = null,

    val stroceBottom: isUiState? = null,
    val typeQuestionCheckBoxState: CheckBoxUiState? = null,
    val bBeforeEditTranslate: TextUiState? = null,
    val bAfterEditTranslate: TextUiState? = null,

    val addAnswerButtonUiState: TextUiState? = null,
    val addTranslateButtonUiState: TextUiState? = null,
    val addGapButtonUiState: TextUiState? = null,
    val cancelButtonUiState: TextUiState? = null,
    val saveQuizButtonUiState: TextUiState? = null,

    val categoryList: List<String>? = null,
    val subCategoryList: List<String>? = null,
    val subsubCategoryList: List<String>? = null,

    val questionList: List<QuestionLocal>? = listOf()
)


sealed class TextUiState {
    object Hidden : TextUiState()
    data class Visible(val text: String? = null, val isEnabled: Boolean = true) : TextUiState()
}

sealed class ImageUiState {
    object Hidden : ImageUiState()
    data class Visible(val imageUri: Uri? = null, val isEnabled: Boolean = true) : ImageUiState()
}

sealed class isUiState {
    data object Hidden : isUiState()
    data object Visible : isUiState()
}

val lastIndex: Int? = null
val dontEdit: List<String>? = null

sealed class SpinnerUiState {
    object Hidden : SpinnerUiState()
    data class Visible(
        val items: List<String>? = dontEdit,
        val selectedIndex: Int? = lastIndex,
        val isEnabled: Boolean = true
    ) : SpinnerUiState()
}

sealed class CheckBoxUiState {
    data object Hidden : CheckBoxUiState()
    data class Visible(val isChecked: Boolean? = null, val isEnabled: Boolean? = null, val text: String? = null) :
        CheckBoxUiState()
}

fun CheckBoxUiState.getType(): Boolean = when (this) {
    is CheckBoxUiState.Hidden -> false
    is CheckBoxUiState.Visible -> isChecked ?: false
}
