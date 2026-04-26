package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component

sealed interface QuizzesChild {
    data object Idle : QuizzesChild
    data class QuestList(val component: QuestListComponent) : QuizzesChild
    data class SectionList(val component: SectionListComponent) : QuizzesChild
    data class ThemeList(val component: ThemeListComponent) : QuizzesChild
    data class LessonList(val component: LessonListComponent) : QuizzesChild
    data class LessonPlaceholder(val component: LessonPlaceholderComponent) : QuizzesChild
}
