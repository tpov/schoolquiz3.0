package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component

import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerRootComponent
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component.PrivateQuizRunnerComponent

sealed interface QuizzesChild {
    data object Idle : QuizzesChild

    data class ArenaLessons(val component: ArenaLessonCatalogComponent) : QuizzesChild

    data class PublicQuestCatalogPicker(val component: PublicQuestCatalogPickerComponent) : QuizzesChild

    data class QuestList(val component: QuestListComponent) : QuizzesChild

    data class SectionList(val component: SectionListComponent) : QuizzesChild

    data class ThemeList(val component: ThemeListComponent) : QuizzesChild

    data class LessonList(val component: LessonListComponent) : QuizzesChild

    data class LessonRunner(val component: LessonRunnerRootComponent) : QuizzesChild

    data class PrivateQuizRunner(val component: PrivateQuizRunnerComponent) : QuizzesChild

    data class LessonEarnings(val component: LessonEarningsComponent) : QuizzesChild
}
