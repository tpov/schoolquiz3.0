package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component

import com.arkivanov.decompose.value.Value
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyItemUi
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyListUiState

interface SectionListComponent {
    val uiState: Value<HierarchyListUiState>
    val titles: List<String>
    fun onSectionClick(section: HierarchyItemUi)
}
