package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake

import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.SectionListComponent
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyItemUi
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyListUiState

class FakeSectionListComponent(
    initialState: HierarchyListUiState,
    override val titles: List<String> = emptyList(),
) : SectionListComponent {

    private val _uiState = MutableValue(initialState)
    override val uiState: Value<HierarchyListUiState> get() = _uiState

    var onSectionClickCalled: HierarchyItemUi? = null

    fun setState(state: HierarchyListUiState) {
        _uiState.value = state
    }

    override fun onSectionClick(section: HierarchyItemUi) {
        onSectionClickCalled = section
    }
}
