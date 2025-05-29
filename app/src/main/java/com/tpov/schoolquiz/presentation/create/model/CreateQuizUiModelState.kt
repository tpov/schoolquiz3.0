package com.tpov.schoolquiz.presentation.create.model

import android.net.Uri
import com.tpov.common.data.model.local.QuestionEntity

data class CreateQuizUiModelState(
    val quizNameUiState: TextUiState? = null,
    val quizImageUiState: ImageUiState? = null,

    val categorySpinnerUiState: SpinnerUiState? = null,
    val subCategorySpinnerUiState: SpinnerUiState? = null,
    val subsubCategorySpinnerUiState: SpinnerUiState? = null,

    val llCreateNewCategory:ContainerUiState? = null,
    val tvCategory: TextUiState? = null,
    val imvCategory: ImageUiState? = null,
    val tvSubCategory: TextUiState? = null,
    val imvSubCategory: ImageUiState? = null,
    val tvSubsubCategory: TextUiState? = null,
    val imvSubsubCategory: ImageUiState? = null,

    val stroceTop: ContainerUiState? = null,
    val questionImageUiState: ImageUiState? = null,
    val fullscreenButtonUiState: CheckBoxUiState? = null,
    val questionNumberSpinnerUiState: SpinnerUiState? = null,

    val stroceBottom: ContainerUiState? = null,
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

val questionList: List<QuestionEntity>? = listOf()
)


sealed class TextUiState {
    object Hidden : TextUiState()
    data class Visible(val text: String? = null, val isEnabled: Boolean = true) : TextUiState()
}

sealed class ImageUiState {
    object Hidden : ImageUiState()
    data class Visible(val imageUri: Uri? = null, val isEnabled: Boolean = true) : ImageUiState()
}
sealed class ContainerUiState {
    data object Hidden : ContainerUiState()
    data object Visible : ContainerUiState()
}

val lastIndex: Int? = null
val dontEdit: List<String>? = null
sealed class SpinnerUiState {
    object Hidden : SpinnerUiState()
    data class Visible(val items: List<String>? = dontEdit, val selectedIndex: Int? = lastIndex, val isEnabled: Boolean = true) : SpinnerUiState()
}

sealed class CheckBoxUiState {
    data object Hidden : CheckBoxUiState()
    data class Visible(val isChecked: Boolean? = null, val isEnabled: Boolean? = null, val text: String? = null) : CheckBoxUiState()
}

