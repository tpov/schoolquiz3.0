package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config

import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ArenaFreshStart
import com.tpov.schoolquiz.shared.core.catalog.domain.model.QuestType
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.SessionMode
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionStamp
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
sealed class QuizzesConfig {
    @Serializable
    data object Idle : QuizzesConfig()

    /** Read-only route only: never serialize an owner, response, cursor or entry permission. */
    @Serializable
    data object ArenaLessons : QuizzesConfig()

    /** Persist a pointer only. Restore never retains fresh-launch permission or an old process epoch. */
    @Serializable
    data class ArenaRunner(
        val ownerUid: String,
        val ticketId: String,
        @Transient val fresh: ArenaFreshStart? = null,
        @Transient val initiatingSession: ArenaSessionStamp? = null,
    ) : QuizzesConfig() {
        init {
            require(ownerUid.isNotBlank() && ticketId.isNotBlank())
        }
    }

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

    /**
     * Прогон приватного квиза (AD-29, история 8.4) — только указатель. Право решает дверь:
     * ни владелец, ни «разрешение на прогон» не сериализуются, и попытка переживает лишь
     * живой процесс (смерть процесса = попытка закрыта серверным тайм-аутом, AD-31).
     */
    @Serializable
    data class PrivateQuizRunner(
        val ownerUid: String,
        val catalogId: String,
        val questId: String,
        val title: String,
        val breadcrumbs: List<BreadcrumbRoot>,
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
