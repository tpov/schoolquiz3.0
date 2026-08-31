package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component

import com.arkivanov.decompose.value.Value
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.BreadcrumbRoot
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.LessonItemUi
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.LessonListUiState

interface LessonListComponent {
    val uiState: Value<LessonListUiState>
    val breadcrumbs: List<BreadcrumbRoot>

    fun onLessonClick(lesson: LessonItemUi)

    fun onHardCheckToggled(lessonId: String)

    /** Asked for when a shut lesson is tapped. Opening it costs nolics and goes through the server. */
    fun onUnlockClick(lesson: LessonItemUi)
}
