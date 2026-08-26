package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component

import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.BreadcrumbRoot
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.PublicQuestCatalogPickerUiState
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import kotlinx.coroutines.flow.StateFlow

interface PublicQuestCatalogPickerComponent {
    val state: StateFlow<PublicQuestCatalogPickerUiState>
    val breadcrumbs: List<BreadcrumbRoot>

    fun onCatalogClick(
        id: CatalogId,
        name: String,
    )
}
