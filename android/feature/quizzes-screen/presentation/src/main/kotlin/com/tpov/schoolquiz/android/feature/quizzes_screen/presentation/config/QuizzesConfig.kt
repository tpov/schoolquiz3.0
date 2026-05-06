package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config

import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import kotlinx.serialization.Serializable

@Serializable
sealed class QuizzesConfig {
    @Serializable
    data object Idle : QuizzesConfig()

    @Serializable
    data class QuestList(
        val catalogId: String,
        val titles: List<String>,
        val shelf: String = "home",
        val mode: QuestListMode = QuestListMode.Home,
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
    data class LessonRunner(
        val lessonId: String,
        val mode: Difficulty,
        val titles: List<String>,
    ) : QuizzesConfig()
}

@Serializable
enum class QuestListMode {
    Home,
    Archive,
    Arena,
}
