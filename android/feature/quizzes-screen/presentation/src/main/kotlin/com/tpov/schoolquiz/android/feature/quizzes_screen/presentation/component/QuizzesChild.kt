package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component

import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerRootComponent

sealed interface QuizzesChild {
    data object Idle : QuizzesChild

    data class PublicQuestCatalogPicker(val component: PublicQuestCatalogPickerComponent) : QuizzesChild

    data class QuestList(val component: QuestListComponent) : QuizzesChild

    data class SectionList(val component: SectionListComponent) : QuizzesChild

    data class ThemeList(val component: ThemeListComponent) : QuizzesChild

    data class LessonList(val component: LessonListComponent) : QuizzesChild

    data class LessonRunner(val component: LessonRunnerRootComponent) : QuizzesChild
}
