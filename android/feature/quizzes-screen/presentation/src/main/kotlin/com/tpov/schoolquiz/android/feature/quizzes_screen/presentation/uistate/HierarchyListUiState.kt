package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate

sealed interface HierarchyListUiState {
    data object Loading : HierarchyListUiState

    data class Empty(val level: HierarchyLevel) : HierarchyListUiState

    data class Loaded(val items: List<HierarchyItemUi>) : HierarchyListUiState
}
