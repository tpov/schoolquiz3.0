package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake

import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.LessonListComponent
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyItemUi
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyListUiState

class FakeLessonListComponent(
    initialState: HierarchyListUiState,
    override val titles: List<String> = emptyList(),
) : LessonListComponent {

    private val _uiState = MutableValue(initialState)
    override val uiState: Value<HierarchyListUiState> get() = _uiState

    var onLessonClickCalled: HierarchyItemUi? = null

    fun setState(state: HierarchyListUiState) {
        _uiState.value = state
    }

    override fun onLessonClick(lesson: HierarchyItemUi) {
        onLessonClickCalled = lesson
    }
}
