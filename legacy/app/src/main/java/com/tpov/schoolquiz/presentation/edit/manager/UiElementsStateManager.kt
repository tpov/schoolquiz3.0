package com.tpov.schoolquiz.presentation.edit.manager

import android.graphics.drawable.BitmapDrawable
import com.tpov.schoolquiz.presentation.edit.model.QuizUiModelState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * РЕФАКТОРИНГ: UiElementsStateManager станет еще проще с UiState<T>
 *
 * БЫЛО:
 * val newQuestionDrawable = state.questionDrawable.toMutableMap()
 * newQuestionDrawable[Pair(questionNumber, isHard)] = bitmapDrawable
 * state.copy(questionDrawable = HashMap(newQuestionDrawable))
 *
 * СТАНЕТ с UiState<BitmapDrawable>:
 * state.copy(
 *     questionImage = UiState.Visible(bitmapDrawable),
 *     questionDrawable = state.questionDrawable.apply {
 *         put(Pair(questionNumber, isHard), bitmapDrawable)
 *     }
 * )
 *
 * Или можно вынести questionDrawable в отдельный state:
 * state.copy(questionImages = UiState.Visible(updatedMap))
 */
class UiElementsStateManager(private val uiState: MutableStateFlow<QuizUiModelState>) {

    /**
     * РЕФАКТОРИНГ: Метод можно упростить с UiState<BitmapDrawable>
     *
     * Дополнительно можно добавить:
     * - UiState для текущего изображения вопроса
     * - Валидацию данных
     * - Логирование изменений
     */
    fun setPhotoQuestion(bitmapDrawable: BitmapDrawable, questionNumber: Int, isHard: Boolean) {
        uiState.update { state ->
            // РЕФАКТОРИНГ: HashMap операции можно заменить на immutable подход
            val newQuestionDrawable = state.questionDrawable.toMutableMap()
            newQuestionDrawable[Pair(questionNumber, isHard)] = bitmapDrawable
            state.copy(questionDrawable = HashMap(newQuestionDrawable))
        }
    }
}
