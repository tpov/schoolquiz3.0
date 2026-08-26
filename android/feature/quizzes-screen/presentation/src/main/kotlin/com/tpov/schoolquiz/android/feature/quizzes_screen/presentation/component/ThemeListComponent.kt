package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component

import com.arkivanov.decompose.value.Value
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.BreadcrumbRoot
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyItemUi
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyListUiState

interface ThemeListComponent {
    val uiState: Value<HierarchyListUiState>
    val breadcrumbs: List<BreadcrumbRoot>

    fun onThemeClick(theme: HierarchyItemUi)
}
