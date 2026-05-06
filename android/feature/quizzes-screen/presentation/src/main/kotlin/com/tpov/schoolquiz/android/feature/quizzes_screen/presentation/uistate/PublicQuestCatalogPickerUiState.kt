package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate

import com.tpov.schoolquiz.android.core.designsystem.model.CatalogDisplayItem

sealed interface PublicQuestCatalogPickerUiState {
    data object Loading : PublicQuestCatalogPickerUiState

    data object Empty : PublicQuestCatalogPickerUiState

    data class Loaded(val catalogs: List<CatalogDisplayItem>) : PublicQuestCatalogPickerUiState
}
