package com.tpov.schoolquiz.shared.core.persistence.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tpov.schoolquiz.shared.core.persistence.AppDatabase
import com.tpov.schoolquiz.shared.core.persistence.DraftLessonEntity
import com.tpov.schoolquiz.shared.core.persistence.DraftQuestionEntity
import com.tpov.schoolquiz.shared.core.persistence.DraftSectionEntity
import com.tpov.schoolquiz.shared.core.persistence.DraftThemeEntity
import com.tpov.schoolquiz.shared.core.persistence.QuestAuthoringDao
import com.tpov.schoolquiz.shared.core.persistence.QuestDraftEntity
import com.tpov.schoolquiz.shared.core.persistence.StringSetConverter
import com.tpov.schoolquiz.shared.core.persistence.TopParticipantListConverter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuestAuthoringDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: QuestAuthoringDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = androidx.room.Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .addTypeConverter(StringSetConverter())
            .addTypeConverter(TopParticipantListConverter())
            .build()
        dao = db.questAuthoringDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun saveDraft_thenSummaryContainsQuestionCount() = runTest {
        dao.saveDraft(
            draft = draftEntity(id = "draft-1", updatedAtMs = 10L),
            sections = listOf(sectionEntity()),
            themes = listOf(themeEntity()),
            lessons = listOf(lessonEntity()),
            questions = listOf(questionEntity()),
        )

        val summaries = dao.observeDraftSummaries("owner-1").first()

        assert(summaries.size == 1) { "Expected one summary, got $summaries" }
        assert(summaries.first().id == "draft-1") { "Unexpected summary id: ${summaries.first().id}" }
        assert(summaries.first().questionCount == 1) {
            "Expected questionCount=1, got ${summaries.first().questionCount}"
        }
    }

    @Test
    fun saveActiveDraft_deactivatesPreviousActiveDraftForSameOwner() = runTest {
        dao.saveDraft(
            draft = draftEntity(id = "draft-1", updatedAtMs = 10L),
            sections = listOf(sectionEntity(draftId = "draft-1")),
            themes = listOf(themeEntity(draftId = "draft-1")),
            lessons = listOf(lessonEntity(draftId = "draft-1")),
            questions = emptyList(),
        )
        dao.saveDraft(
            draft = draftEntity(id = "draft-2", updatedAtMs = 20L),
            sections = listOf(sectionEntity(draftId = "draft-2")),
            themes = listOf(themeEntity(draftId = "draft-2")),
            lessons = listOf(lessonEntity(draftId = "draft-2")),
            questions = emptyList(),
        )

        val first = requireNotNull(dao.findDraftById("draft-1"))
        val second = requireNotNull(dao.findDraftById("draft-2"))

        assert(!first.isActive) { "First draft should be deactivated" }
        assert(second.isActive) { "Second draft should stay active" }
    }

    @Test
    fun upsertQuestion_touchesDraftRevisionAndUpdatedAt() = runTest {
        dao.saveDraft(
            draft = draftEntity(id = "draft-1", updatedAtMs = 10L),
            sections = listOf(sectionEntity()),
            themes = listOf(themeEntity()),
            lessons = listOf(lessonEntity()),
            questions = emptyList(),
        )

        dao.upsertQuestion(questionEntity(updatedAtMs = 99L))

        val draft = requireNotNull(dao.findDraftById("draft-1"))
        val questions = dao.findQuestions("draft-1")
        assert(draft.localRevision == 2L) { "Expected revision 2, got ${draft.localRevision}" }
        assert(draft.updatedAtMs == 99L) { "Expected updatedAtMs 99, got ${draft.updatedAtMs}" }
        assert(questions.single().id == "question-1") { "Question should be inserted" }
    }

    private fun draftEntity(
        id: String,
        updatedAtMs: Long,
    ): QuestDraftEntity =
        QuestDraftEntity(
            id = id,
            ownerUid = "owner-1",
            catalogId = "catalog-1",
            title = "Draft $id",
            description = null,
            defaultLanguage = "ru",
            defaultDifficulty = "EASY",
            status = "DRAFT",
            localRevision = 1L,
            serverRevision = null,
            publicQuestId = null,
            createdAtMs = 1L,
            updatedAtMs = updatedAtMs,
            isActive = true,
        )

    private fun sectionEntity(draftId: String = "draft-1"): DraftSectionEntity =
        DraftSectionEntity(
            id = "section-$draftId",
            draftId = draftId,
            title = "Section",
            order = 0,
        )

    private fun themeEntity(draftId: String = "draft-1"): DraftThemeEntity =
        DraftThemeEntity(
            id = "theme-$draftId",
            draftId = draftId,
            sectionId = "section-$draftId",
            title = "Theme",
            order = 0,
        )

    private fun lessonEntity(draftId: String = "draft-1"): DraftLessonEntity =
        DraftLessonEntity(
            id = "lesson-$draftId",
            draftId = draftId,
            themeId = "theme-$draftId",
            title = "Lesson",
            order = 0,
        )

    private fun questionEntity(
        draftId: String = "draft-1",
        updatedAtMs: Long = 11L,
    ): DraftQuestionEntity =
        DraftQuestionEntity(
            id = "question-1",
            draftId = draftId,
            lessonId = "lesson-$draftId",
            type = "SINGLE_CHOICE",
            language = "ru",
            difficulty = "EASY",
            order = 0,
            text = "Question?",
            imagePath = null,
            payload = """{"type":"single_choice"}""",
            validationState = "SAVED",
            updatedAtMs = updatedAtMs,
        )
}
