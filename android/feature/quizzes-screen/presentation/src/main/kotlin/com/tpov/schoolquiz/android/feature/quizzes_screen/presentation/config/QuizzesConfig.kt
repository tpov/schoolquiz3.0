package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config

import kotlinx.serialization.Serializable

@Serializable
sealed class QuizzesConfig {

    @Serializable
    data object Idle : QuizzesConfig()

    @Serializable
    data class QuestList(
        val catalogId: String,
        val titles: List<String>,
    ) : QuizzesConfig()

    @Serializable
    data class SectionList(
        val questId: String,
        val titles: List<String>,
    ) : QuizzesConfig()

    @Serializable
    data class ThemeList(
        val sectionId: String,
        val titles: List<String>,
    ) : QuizzesConfig()

    @Serializable
    data class LessonList(
        val themeId: String,
        val titles: List<String>,
    ) : QuizzesConfig()

    @Serializable
    data class LessonPlaceholder(
        val lessonId: String,
        val lessonTitle: String,
        val titles: List<String>,
    ) : QuizzesConfig()
}
