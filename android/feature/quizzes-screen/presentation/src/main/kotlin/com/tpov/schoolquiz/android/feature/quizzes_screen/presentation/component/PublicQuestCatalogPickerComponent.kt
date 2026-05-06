package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component

import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.PublicQuestCatalogPickerUiState
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import kotlinx.coroutines.flow.StateFlow

interface PublicQuestCatalogPickerComponent {
    val state: StateFlow<PublicQuestCatalogPickerUiState>
    val titles: List<String>

    fun onCatalogClick(
        id: CatalogId,
        name: String,
    )
}
