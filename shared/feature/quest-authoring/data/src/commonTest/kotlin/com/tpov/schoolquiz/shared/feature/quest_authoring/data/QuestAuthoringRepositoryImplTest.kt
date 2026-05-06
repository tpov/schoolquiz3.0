package com.tpov.schoolquiz.shared.feature.quest_authoring.data

import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.core.persistence.DraftLessonEntity
import com.tpov.schoolquiz.shared.core.persistence.DraftQuestionEntity
import com.tpov.schoolquiz.shared.core.persistence.DraftSectionEntity
import com.tpov.schoolquiz.shared.core.persistence.DraftThemeEntity
import com.tpov.schoolquiz.shared.core.persistence.QuestArenaSubmissionEntity
import com.tpov.schoolquiz.shared.core.persistence.QuestDraftEntity
import com.tpov.schoolquiz.shared.core.persistence.QuestDraftSummaryEntity
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.PrivateQuestSnapshot
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class QuestAuthoringRepositoryImplTest {

    @Test
    fun observeDraftSummaries_mapsLocalProjectionToDomain() = runTest {
        val local = FakeQuestAuthoringLocalDataSource()
        val repository = QuestAuthoringRepositoryImpl(local)
        local.summaries.value = listOf(
            QuestDraftSummaryEntity(
                id = "draft-1",
                catalogId = "catalog-1",
                title = "My draft",
                status = "SAVED",
                questionCount = 2,
                updatedAtMs = 42L,
                isActive = true,
            ),
        )

        val result = repository.observeDraftSummaries("owner-1").first()

        assertEquals(1, result.size)
        assertEquals(QuestDraftId("draft-1"), result.first().id)
        assertEquals(CatalogId("catalog-1"), result.first().catalogId)
        assertEquals(QuestDraftStatus.SAVED, result.first().status)
        assertEquals(2, result.first().questionCount)
        assertTrue(result.first().isActive)
    }

    @Test
    fun saveDraft_mapsDomainBundleToEntities() = runTest {
        val local = FakeQuestAuthoringLocalDataSource()
        val repository = QuestAuthoringRepositoryImpl(local)

        val result = repository.saveDraft(domainBundle())

        assertTrue(result.isSuccess)
        val saved = assertNotNull(local.savedBundle)
        assertEquals("draft-1", saved.draft.id)
        assertEquals("owner-1", saved.draft.ownerUid)
        assertEquals("EASY", saved.draft.defaultDifficulty)
        assertEquals("section-1", saved.sections.single().id)
        assertEquals("theme-1", saved.themes.single().id)
        assertEquals("lesson-1", saved.lessons.single().id)
        assertEquals("question-1", saved.questions.single().id)
    }

    @Test
    fun getActiveDraft_mapsEntityBundleToDomain() = runTest {
        val local = FakeQuestAuthoringLocalDataSource()
        val repository = QuestAuthoringRepositoryImpl(local)
        local.activeDraft = entityBundle()

        val result = repository.getActiveDraft("owner-1")

        assertNotNull(result)
        assertEquals(QuestDraftId("draft-1"), result.draft.id)
        assertEquals(DraftLessonId("lesson-1"), result.lessons.single().id)
        assertEquals(DraftQuestionType.SINGLE_CHOICE, result.questions.single().type)
        assertEquals(Difficulty.EASY, result.questions.single().difficulty)
    }

    @Test
    fun upsertQuestion_mapsDomainQuestionToEntity() = runTest {
        val local = FakeQuestAuthoringLocalDataSource()
        val repository = QuestAuthoringRepositoryImpl(local)

        val result = repository.upsertQuestion(domainQuestion())

        assertTrue(result.isSuccess)
        val question = assertNotNull(local.upsertedQuestion)
        assertEquals("question-1", question.id)
        assertEquals("SINGLE_CHOICE", question.type)
        assertEquals("EASY", question.difficulty)
        assertEquals("SAVED", question.validationState)
    }

    @Test
    fun queueArenaSubmission_mapsDomainSubmissionToEntity() = runTest {
        val local = FakeQuestAuthoringLocalDataSource()
        val repository = QuestAuthoringRepositoryImpl(local)

        val result = repository.queueArenaSubmission(domainSubmission())

        assertTrue(result.isSuccess)
        val submission = assertNotNull(local.queuedSubmission)
        assertEquals("submission-1", submission.id)
        assertEquals("draft-1", submission.draftId)
        assertEquals("owner-1", submission.ownerUid)
    }

    @Test
    fun localFailure_returnsResultFailure() = runTest {
        val local = FakeQuestAuthoringLocalDataSource()
        val repository = QuestAuthoringRepositoryImpl(local)
        local.saveFailure = IllegalStateException("room failed")

        val result = repository.saveDraft(domainBundle())

        assertFalse(result.isSuccess)
        assertEquals("room failed", result.exceptionOrNull()?.message)
    }

    private class FakeQuestAuthoringLocalDataSource : QuestAuthoringLocalDataSource {
        val summaries = MutableStateFlow<List<QuestDraftSummaryEntity>>(emptyList())
        val draftFlow = MutableStateFlow<QuestAuthoringEntityBundle?>(null)
        var draft: QuestAuthoringEntityBundle? = null
        var activeDraft: QuestAuthoringEntityBundle? = null
        var savedBundle: QuestAuthoringEntityBundle? = null
        var upsertedQuestion: DraftQuestionEntity? = null
        var queuedSubmission: QuestArenaSubmissionEntity? = null
        var statusUpdate: StatusUpdate? = null
        var saveFailure: Exception? = null

        override fun observeDraftSummaries(ownerUid: String): Flow<List<QuestDraftSummaryEntity>> =
            summaries

        override fun observeDraft(draftId: String): Flow<QuestAuthoringEntityBundle?> =
            draftFlow

        override suspend fun getDraft(draftId: String): QuestAuthoringEntityBundle? =
            draft

        override suspend fun getActiveDraft(ownerUid: String): QuestAuthoringEntityBundle? =
            activeDraft

        override suspend fun saveDraft(bundle: QuestAuthoringEntityBundle) {
            saveFailure?.let { throw it }
            savedBundle = bundle
        }

        override suspend fun upsertQuestion(question: DraftQuestionEntity) {
            upsertedQuestion = question
        }

        override suspend fun queueArenaSubmission(submission: QuestArenaSubmissionEntity) {
            queuedSubmission = submission
        }

        override suspend fun findPendingArenaSubmissions(limit: Int): List<QuestArenaSubmissionEntity> =
            emptyList()

        override suspend fun markArenaSubmissionFailure(
            submissionId: String,
            message: String?,
        ) = Unit

        override suspend fun markArenaSubmissionSent(
            submissionId: String,
            draftId: String,
            updatedAtMs: Long,
        ) = Unit

        override suspend fun upsertSyncedPrivateQuest(snapshot: PrivateQuestSnapshot) = Unit

        override suspend fun setDraftStatus(
            draftId: String,
            status: String,
            updatedAtMs: Long,
        ) {
            statusUpdate = StatusUpdate(draftId, status, updatedAtMs)
        }
    }

    private data class StatusUpdate(
        val draftId: String,
        val status: String,
        val updatedAtMs: Long,
    )

    private fun domainBundle(): QuestAuthoringBundle =
        QuestAuthoringBundle(
            draft = QuestDraft(
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
            ),
            sections = listOf(
                DraftSection(
                    id = DraftSectionId("section-1"),
                    draftId = QuestDraftId("draft-1"),
                    title = "Section",
                    order = 0,
                ),
            ),
            themes = listOf(
                DraftTheme(
                    id = DraftThemeId("theme-1"),
                    draftId = QuestDraftId("draft-1"),
                    sectionId = DraftSectionId("section-1"),
                    title = "Theme",
                    order = 0,
                ),
            ),
            lessons = listOf(
                DraftLesson(
                    id = DraftLessonId("lesson-1"),
                    draftId = QuestDraftId("draft-1"),
                    themeId = DraftThemeId("theme-1"),
                    title = "Lesson",
                    order = 0,
                ),
            ),
            questions = listOf(domainQuestion()),
        )

    private fun domainQuestion(): DraftQuestion =
        DraftQuestion(
            id = DraftQuestionId("question-1"),
            draftId = QuestDraftId("draft-1"),
            lessonId = DraftLessonId("lesson-1"),
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

    private fun domainSubmission(): QuestArenaSubmission =
        QuestArenaSubmission(
            id = QuestArenaSubmissionId("submission-1"),
            draftId = QuestDraftId("draft-1"),
            ownerUid = "owner-1",
            localRevision = 3L,
            requestedAtMs = 4L,
            lessonIds = setOf(DraftLessonId("lesson-1")),
            targetShelf = "arena",
        )

    private fun entityBundle(): QuestAuthoringEntityBundle =
        QuestAuthoringEntityBundle(
            draft = QuestDraftEntity(
                id = "draft-1",
                ownerUid = "owner-1",
                catalogId = "catalog-1",
                title = "My draft",
                description = null,
                defaultLanguage = "ru",
                defaultDifficulty = "EASY",
                status = "SAVED",
                localRevision = 1L,
                serverRevision = null,
                publicQuestId = null,
                createdAtMs = 1L,
                updatedAtMs = 2L,
                isActive = true,
            ),
            sections = listOf(DraftSectionEntity("section-1", "draft-1", "Section", 0)),
            themes = listOf(DraftThemeEntity("theme-1", "draft-1", "section-1", "Theme", 0)),
            lessons = listOf(DraftLessonEntity("lesson-1", "draft-1", "theme-1", "Lesson", 0)),
            questions = listOf(
                DraftQuestionEntity(
                    id = "question-1",
                    draftId = "draft-1",
                    lessonId = "lesson-1",
                    type = "SINGLE_CHOICE",
                    language = "ru",
                    difficulty = "EASY",
                    order = 0,
                    text = "Question?",
                    imagePath = null,
                    payload = """{"type":"single_choice"}""",
                    validationState = "SAVED",
                    updatedAtMs = 3L,
                ),
            ),
        )
}
