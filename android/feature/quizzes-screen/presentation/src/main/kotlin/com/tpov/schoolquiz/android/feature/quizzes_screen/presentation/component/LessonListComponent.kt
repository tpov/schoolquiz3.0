package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component

import com.arkivanov.decompose.value.Value
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.BreadcrumbRoot
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.LessonItemUi
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.LessonListUiState
import kotlinx.coroutines.flow.Flow

interface LessonListComponent {
    val uiState: Value<LessonListUiState>

    /**
     * One-shot messages for the player — today, why a purchase did not happen.
     *
     * A stream rather than state because there is nothing to keep: the row is still locked, which
     * is the durable part, and the reason is read once. Single consumer by contract.
     */
    val messages: Flow<String>
    val breadcrumbs: List<BreadcrumbRoot>

    fun onLessonClick(lesson: LessonItemUi)

    fun onHardCheckToggled(lessonId: String)

    /** Asked for when a shut lesson is tapped. Opening it costs nolics and goes through the server. */
    fun onUnlockClick(lesson: LessonItemUi)
}
