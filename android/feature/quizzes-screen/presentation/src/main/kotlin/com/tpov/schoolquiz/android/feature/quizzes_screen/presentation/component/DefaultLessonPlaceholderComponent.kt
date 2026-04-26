package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component

import com.arkivanov.decompose.ComponentContext
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.LessonPlaceholderUiState

class DefaultLessonPlaceholderComponent(
    componentContext: ComponentContext,
    config: QuizzesConfig.LessonPlaceholder,
) : ComponentContext by componentContext, LessonPlaceholderComponent {

    override val uiState = LessonPlaceholderUiState(
        lessonTitle = config.lessonTitle,
        titles = config.titles,
    )
}
