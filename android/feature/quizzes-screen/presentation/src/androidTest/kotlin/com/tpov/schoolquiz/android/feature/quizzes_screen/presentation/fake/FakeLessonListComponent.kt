package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake

import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.LessonListComponent
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.LessonItemUi
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.LessonListUiState

class FakeLessonListComponent(
    initialState: LessonListUiState,
    override val titles: List<String> = emptyList(),
) : LessonListComponent {

    private val _uiState = MutableValue(initialState)
    override val uiState: Value<LessonListUiState> get() = _uiState

    var onLessonClickCalled: LessonItemUi? = null
    var onHardCheckToggledCalled: String? = null

    fun setState(state: LessonListUiState) {
        _uiState.value = state
    }

    override fun onLessonClick(lesson: LessonItemUi) {
        onLessonClickCalled = lesson
    }

    override fun onHardCheckToggled(lessonId: String) {
        onHardCheckToggledCalled = lessonId
    }
}
