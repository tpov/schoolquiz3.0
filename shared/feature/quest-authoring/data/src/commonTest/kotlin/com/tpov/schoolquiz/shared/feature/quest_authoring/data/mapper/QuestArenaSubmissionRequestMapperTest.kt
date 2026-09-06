package com.tpov.schoolquiz.shared.feature.quest_authoring.data.mapper

import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.mapper.QuestArenaSubmissionRequestMapper.toRequest
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftLesson
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftLessonId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftQuestion
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftQuestionId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftQuestionType
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftQuestionValidationState
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftSection
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftSectionId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftTheme
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftThemeId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestArenaSubmission
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestArenaSubmissionId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestAuthoringBundle
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraft
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraftId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraftStatus
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Заявка несёт порцию, а состав черновика — целиком.
 *
 * Без второго сервер не отличает «урок удалён автором» от «урок не в этой порции» и потому не
 * снимает ни урока, ни темы, ни раздела: снятие по отсутствию архивировало бы весь курс при
 * публикации одного урока.
 */
class QuestArenaSubmissionRequestMapperTest {

    @Test
    fun toRequest_namesTheWholeDraftWhileTheBatchStaysTheChosenLessonsOnly() {
        val request = submission(setOf(DraftLessonId("lesson-1"))).toRequest(twoBranchBundle())

        // Порция — только выбранный урок и его ветка.
        assertEquals(setOf("lesson-1"), request.targetLessonIds)
        assertEquals(listOf("lesson-1"), request.lessons.map { it.id })
        assertEquals(listOf("theme-1"), request.themes.map { it.id })
        assertEquals(listOf("section-1"), request.sections.map { it.id })

        // Состав черновика — всё, что у автора осталось, включая непубликуемую ветку.
        assertEquals(setOf("section-1", "section-2"), request.draftTree.sections)
        assertEquals(setOf("theme-1", "theme-2"), request.draftTree.themes)
        assertEquals(setOf("lesson-1", "lesson-2", "lesson-3"), request.draftTree.lessons)
    }

    @Test
    fun toRequest_ofADraftWithoutNodesNamesThreeEmptyLists() {
        // Пустой черновик — три пустых списка, а не отсутствующее поле: решение «снимать нечего»
        // принимает сервер, и молчание клиента ему для этого не нужно.
        val request = submission(setOf(DraftLessonId("lesson-1"))).toRequest(emptyBundle())

        assertEquals(emptySet<String>(), request.draftTree.sections)
        assertEquals(emptySet<String>(), request.draftTree.themes)
        assertEquals(emptySet<String>(), request.draftTree.lessons)
    }

    private fun submission(lessonIds: Set<DraftLessonId>): QuestArenaSubmission =
        QuestArenaSubmission(
            id = QuestArenaSubmissionId("submission-1"),
            draftId = QuestDraftId("draft-1"),
            ownerUid = "owner-1",
            expectedVersion = 3L,
            requestedAtMs = 4L,
            lessonIds = lessonIds,
            targetShelf = "arena",
        )

    private fun emptyBundle(): QuestAuthoringBundle =
        QuestAuthoringBundle(
            draft = draft(),
            sections = emptyList(),
            themes = emptyList(),
            lessons = emptyList(),
            questions = emptyList(),
        )

    /** Черновик из двух разделов: публикуется один урок первой ветки, вторая остаётся в столе. */
    private fun twoBranchBundle(): QuestAuthoringBundle =
        QuestAuthoringBundle(
            draft = draft(),
            sections = listOf(section("section-1", 0), section("section-2", 1)),
            themes = listOf(theme("theme-1", "section-1", 0), theme("theme-2", "section-2", 1)),
            lessons = listOf(
                lesson("lesson-1", "theme-1", 0),
                lesson("lesson-2", "theme-1", 1),
                lesson("lesson-3", "theme-2", 2),
            ),
            questions = listOf(
                question("question-1", "lesson-1"),
                question("question-2", "lesson-2"),
                question("question-3", "lesson-3"),
            ),
        )

    private fun draft(): QuestDraft =
        QuestDraft(
            id = QuestDraftId("draft-1"),
            ownerUid = "owner-1",
            catalogId = CatalogId("catalog-1"),
            title = "My draft",
            description = null,
            defaultLanguage = "ru",
            defaultDifficulty = Difficulty.EASY,
            status = QuestDraftStatus.SAVED,
            localRevision = 1L,
            serverRevision = null,
            publicQuestId = null,
            createdAtMs = 1L,
            updatedAtMs = 2L,
            isActive = true,
        )

    private fun section(
        id: String,
        order: Int,
    ): DraftSection =
        DraftSection(
            id = DraftSectionId(id),
            draftId = QuestDraftId("draft-1"),
            title = "Section $id",
            order = order,
        )

    private fun theme(
        id: String,
        sectionId: String,
        order: Int,
    ): DraftTheme =
        DraftTheme(
            id = DraftThemeId(id),
            draftId = QuestDraftId("draft-1"),
            sectionId = DraftSectionId(sectionId),
            title = "Theme $id",
            order = order,
        )

    private fun lesson(
        id: String,
        themeId: String,
        order: Int,
    ): DraftLesson =
        DraftLesson(
            id = DraftLessonId(id),
            draftId = QuestDraftId("draft-1"),
            themeId = DraftThemeId(themeId),
            title = "Lesson $id",
            order = order,
        )

    private fun question(
        id: String,
        lessonId: String,
    ): DraftQuestion =
        DraftQuestion(
            id = DraftQuestionId(id),
            draftId = QuestDraftId("draft-1"),
            lessonId = DraftLessonId(lessonId),
            type = DraftQuestionType.SINGLE_CHOICE,
            language = "ru",
            difficulty = Difficulty.EASY,
            order = 0,
            text = "Question?",
            imagePath = null,
            payload = """{"type":"single_choice"}""",
            validationState = DraftQuestionValidationState.SAVED,
            updatedAtMs = 3L,
        )
}
