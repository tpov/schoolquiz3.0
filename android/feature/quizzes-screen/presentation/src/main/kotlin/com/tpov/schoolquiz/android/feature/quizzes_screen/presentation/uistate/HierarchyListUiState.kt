package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate

sealed interface HierarchyListUiState {
    data object Loading : HierarchyListUiState

    data class Empty(val levelLabel: String) : HierarchyListUiState

    data class Loaded(val items: List<HierarchyItemUi>) : HierarchyListUiState
}
