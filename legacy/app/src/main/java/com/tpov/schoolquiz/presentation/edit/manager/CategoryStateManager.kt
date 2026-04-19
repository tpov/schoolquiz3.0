package com.tpov.schoolquiz.presentation.edit.manager

import com.tpov.schoolquiz.presentation.edit.model.IsUiState
import com.tpov.schoolquiz.presentation.edit.model.QuizUiModelState
import com.tpov.schoolquiz.presentation.edit.model.TextUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * РЕФАКТОРИНГ: Упростится работа с категориями при переходе на UiState<T>
 *
 * БЫЛО:
 * state.copy(
 *     tvCategory = TextUiState.Visible(text = names.first, isVisible = null),
 *     tvSubCategory = TextUiState.Visible(text = names.second, isVisible = null),
 *     tvSubsubCategory = TextUiState.Visible(text = names.third, isVisible = null)
 * )
 *
 * СТАНЕТ:
 * state.copy(
 *     category = UiState.Visible(names.first),
 *     subCategory = UiState.Visible(names.second),
 *     subsubCategory = UiState.Visible(names.third)
 * )
 *
 * Преимущества:
 * - Меньше кода
 * - Единообразие
 * - Типобезопасность
 */
class CategoryStateManager(private val uiState: MutableStateFlow<QuizUiModelState>) {

    /**
     * РЕФАКТОРИНГ: Этот метод станет проще с UiState<String>
     */
    fun selectCategory(names: Triple<String, String, String>) {
        uiState.update { state ->
            state.copy(
                // РЕФАКТОРИНГ: TextUiState.Visible -> UiState.Visible
                tvCategory = TextUiState.Visible(text = names.first, isVisible = null),
                tvSubCategory = TextUiState.Visible(text = names.second, isVisible = null),
                tvSubsubCategory = TextUiState.Visible(text = names.third, isVisible = null)
            )
        }
    }

    /**
     * РЕФАКТОРИНГ: IsUiState -> UiState<Unit> или просто Boolean
     *
     * БЫЛО: IsUiState.Visible
     * СТАНЕТ: UiState.Visible(Unit) или просто createNewCategory = true
     */
    fun toggleNewCategoryFields() {
        uiState.update { state ->
            state.copy(llCreateNewCategory = IsUiState.Visible)
        }
    }
}
