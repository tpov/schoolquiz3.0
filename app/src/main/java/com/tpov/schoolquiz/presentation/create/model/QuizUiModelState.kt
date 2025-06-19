package com.tpov.schoolquiz.presentation.create.model

import android.graphics.drawable.BitmapDrawable
import com.tpov.common.data.model.local.QuestionLocal

/**
 * РЕФАКТОРИНГ ИДЕЯ: Заменить все sealed UI классы на generic UiState<T>
 *
 * sealed class UiState<T> {
 *     object Hidden : UiState<Nothing>()
 *     data class Visible<T>(
 *         val data: T,
 *         val isEnabled: Boolean = true,
 *         val isVisible: Boolean = true
 *     ) : UiState<T>()
 * }
 *
 * Extension функции:
 * fun <T> UiState<T>.isVisible() = this is UiState.Visible
 * fun <T> UiState<T>.dataOrNull() = (this as? UiState.Visible)?.data
 * fun <T> UiState<T>.isEnabled() = (this as? UiState.Visible)?.isEnabled ?: false
 */
data class QuizUiModelState(
    // РЕФАКТОРИНГ: TextUiState -> UiState<String>
    val quizNameUiState: TextUiState? = null,
    // РЕФАКТОРИНГ: ImageUiState -> UiState<BitmapDrawable>
    val quizImageUiState: ImageUiState? = null,

    // РЕФАКТОРИНГ: SpinnerUiState -> UiState<SpinnerData>
    // data class SpinnerData(val items: List<String>, val selectedIndex: Int = 0)
    val categorySpinnerUiState: SpinnerUiState? = null,
    val subCategorySpinnerUiState: SpinnerUiState? = null,
    val subsubCategorySpinnerUiState: SpinnerUiState? = null,

    // РЕФАКТОРИНГ: IsUiState -> UiState<Unit> или просто Boolean
    val llCreateNewCategory: IsUiState? = null,
    // РЕФАКТОРИНГ: TextUiState -> UiState<String>
    val tvCategory: TextUiState? = null,
    // РЕФАКТОРИНГ: ImageUiState -> UiState<BitmapDrawable>
    val imvCategory: ImageUiState? = null,
    val tvSubCategory: TextUiState? = null,
    val imvSubCategory: ImageUiState? = null,
    val tvSubsubCategory: TextUiState? = null,
    val imvSubsubCategory: ImageUiState? = null,

    val stroceTop: IsUiState? = null,
    val questionImageUiState: ImageUiState? = null,
    // РЕФАКТОРИНГ: CheckBoxUiState -> UiState<CheckBoxData>
    // data class CheckBoxData(val isChecked: Boolean, val text: String)
    val fullscreenButtonUiState: CheckBoxUiState? = null,
    val fullScreen: IsUiState? = null,
    var questionNumberSpinnerUiState: SpinnerUiState? = null,
    // РЕФАКТОРИНГ: List<T>? -> UiState<List<T>>
    // Преимущество: проверка видимости и состояния списка в одном месте
    var rvQuestionTranslate: List<TranslateQuestion>? = null,
    val rvAnswerTranslate: List<TranslateAnswer>? = null,

    val stroceBottom: IsUiState? = null,
    val typeQuestionCheckBoxState: CheckBoxUiState? = null,
    val bBeforeEditTranslate: TextUiState? = null,
    val bAfterEditTranslate: TextUiState? = null,

    val addAnswerButtonUiState: TextUiState? = null,
    val addTranslateButtonUiState: TextUiState? = null,
    val cancelButtonUiState: TextUiState? = null,
    val saveQuizButtonUiState: TextUiState? = null,

    // РЕФАКТОРИНГ: List<String>? -> UiState<List<String>>
    val categoryList: List<String>? = null,
    val subCategoryList: List<String>? = null,
    val subsubCategoryList: List<String>? = null,

    val questionList: MutableList<InternalQuestion> = mutableListOf(), // Изменено на InternalQuestion
    val questionListBefore: MutableList<InternalQuestion> = mutableListOf(), // Изменено на InternalQuestion
    val questionDrawable: HashMap<Pair<Int,Boolean>, BitmapDrawable> = hashMapOf(),

    val pathStructure: com.tpov.common.presentation.model.PathStructure? = null, // Добавлено поле
    val errorState: String? = null // Добавлено поле для отображения ошибок загрузки/сохранения
)

/**
 * РЕФАКТОРИНГ: УДАЛИТЬ - заменить на UiState<String>
 * Преимущества:
 * - DRY принцип (не дублировать логику Hidden/Visible)
 * - Типобезопасность (String, Int, etc. вместо Any?)
 * - Единообразие (все UI состояния работают одинаково)
 */
sealed class TextUiState {
    data object Hidden : TextUiState()
    data class Visible(val text: String? = null, val isEnabled: Boolean = true, val isVisible: Boolean? = true) : TextUiState()
}

/**
 * РЕФАКТОРИНГ: УДАЛИТЬ - заменить на UiState<BitmapDrawable>
 */
sealed class ImageUiState {
    data object Hidden : ImageUiState()
    // Изменено imageBitmap на imageUri
    data class Visible(val imageUri: String? = null, val imageBitmap: BitmapDrawable? = null, val isEnabled: Boolean = true) : ImageUiState()
}

data class InternalQuestion(
    val numQuestion: Int,
    val hardQuestion: Boolean,
    var type: com.tpov.common.data.model.QuestionType,
    var question: String, // Текст вопроса на основном языке (или первом загруженном)
    var image: String?, // URI или путь к картинке вопроса
    var answers: MutableList<TranslateAnswer>, // Варианты ответов для основного языка
    var translateQuestion: MutableList<TranslateQuestion>, // Переводы текста вопроса
    var translateAnswers: MutableList<TranslateAnswer> // Переводы вариантов ответов (пока не используется полностью)
)

/**
 * РЕФАКТОРИНГ: УДАЛИТЬ - заменить на UiState<Unit> или Boolean
 */
sealed class IsUiState {
    data object Hidden : IsUiState()
    data object Visible : IsUiState()
}

val lastIndex: Int? = null
val dontEdit: List<String>? = null

/**
 * РЕФАКТОРИНГ: УДАЛИТЬ - заменить на UiState<SpinnerData>
 * data class SpinnerData(
 *     val items: List<String>,
 *     val selectedIndex: Int = 0
 * )
 */
sealed class SpinnerUiState {
    data object Hidden : SpinnerUiState()
    data class Visible(
        val items: List<String>? = dontEdit,
        val selectedIndex: Int? = lastIndex,
        val isEnabled: Boolean = true
    ) : SpinnerUiState()
}

/**
 * РЕФАКТОРИНГ: УДАЛИТЬ - заменить на UiState<CheckBoxData>
 * data class CheckBoxData(
 *     val isChecked: Boolean,
 *     val text: String,
 *     val isInit: Boolean = true
 * )
 */
sealed class CheckBoxUiState {
    data object Hidden : CheckBoxUiState()
    data class Visible(val isChecked: Boolean? = null, val isEnabled: Boolean? = null, val text: String? = null, val isInit : Boolean = true) :
        CheckBoxUiState()
}

fun SpinnerUiState.getNumQuestion(): Int = when (this) {
    is SpinnerUiState.Hidden -> 0
    is SpinnerUiState.Visible -> selectedIndex ?: 0
}

fun CheckBoxUiState.getType(): Boolean = when (this) {
    is CheckBoxUiState.Hidden -> false
    is CheckBoxUiState.Visible -> isChecked ?: false
}
