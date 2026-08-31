package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config

import com.tpov.schoolquiz.shared.core.catalog.domain.model.QuestType
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.SessionMode
import kotlinx.serialization.Serializable

@Serializable
sealed class QuizzesConfig {
    @Serializable
    data object Idle : QuizzesConfig()

    @Serializable
    data class PublicQuestCatalogPicker(
        val targetShelf: String,
        val breadcrumbs: List<BreadcrumbRoot>,
        val selectionTargetShelf: String? = targetShelf,
        val forcedLessonMode: Difficulty? = null,
    ) : QuizzesConfig()

    @Serializable
    data class QuestList(
        val catalogId: String,
        val breadcrumbs: List<BreadcrumbRoot>,
        val shelf: String = "home",
        val mode: QuestListMode = QuestListMode.Home,
        val selectionTargetShelf: String? = null,
        val forcedLessonMode: Difficulty? = null,
        /** Type of the catalog being listed; defaulted so older saved state still restores. */
        val questType: QuestType = QuestType.REGULAR,
    ) : QuizzesConfig()

    @Serializable
    data class SectionList(
        val questId: String,
        val breadcrumbs: List<BreadcrumbRoot>,
        val forcedLessonMode: Difficulty? = null,
        /** Course catalogs gate lessons sequentially; defaulted so older saved state restores. */
        val questType: QuestType = QuestType.REGULAR,
    ) : QuizzesConfig()

    @Serializable
    data class ThemeList(
        val sectionId: String,
        val breadcrumbs: List<BreadcrumbRoot>,
        val forcedLessonMode: Difficulty? = null,
        /** Course catalogs gate lessons sequentially; defaulted so older saved state restores. */
        val questType: QuestType = QuestType.REGULAR,
    ) : QuizzesConfig()

    @Serializable
    data class LessonList(
        val themeId: String,
        val breadcrumbs: List<BreadcrumbRoot>,
        val forcedLessonMode: Difficulty? = null,
        /** Course catalogs gate lessons sequentially; defaulted so older saved state restores. */
        val questType: QuestType = QuestType.REGULAR,
    ) : QuizzesConfig()

    @Serializable
    data class LessonRunner(
        val lessonId: String,
        val mode: Difficulty,
        val breadcrumbs: List<BreadcrumbRoot>,
        /** Defaulted so state saved before exams existed still restores. */
        val sessionMode: SessionMode = SessionMode.LEARNING,
    ) : QuizzesConfig()
}

@Serializable
enum class QuestListMode {
    Home,
    Archive,
    Arena,
}
