package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake

import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.LessonListComponent
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.BreadcrumbRoot
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.LessonItemUi
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.LessonListUiState

class FakeLessonListComponent(
    initialState: LessonListUiState,
    override val breadcrumbs: List<BreadcrumbRoot> = emptyList(),
) : LessonListComponent {

    private val _uiState = MutableValue(initialState)
    override val uiState: Value<LessonListUiState> get() = _uiState

    var onLessonClickCalled: LessonItemUi? = null
    var onHardCheckToggledCalled: String? = null
    var onUnlockClickCalled: LessonItemUi? = null

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    override val messages: Flow<String> = _messages

    /** Pushes a message the screen should surface, so a test can assert the snackbar. */
    suspend fun emitMessage(text: String) {
        _messages.emit(text)
    }

    fun setState(state: LessonListUiState) {
        _uiState.value = state
    }

    override fun onUnlockClick(lesson: LessonItemUi) {
        onUnlockClickCalled = lesson
    }

    override fun onLessonClick(lesson: LessonItemUi) {
        onLessonClickCalled = lesson
    }

    override fun onHardCheckToggled(lessonId: String) {
        onHardCheckToggledCalled = lessonId
    }
}
