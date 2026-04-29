package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate

sealed interface LessonListUiState {
    data object Loading : LessonListUiState

    data class Empty(val levelLabel: String) : LessonListUiState

    data class Loaded(val items: List<LessonItemUi>) : LessonListUiState
}
