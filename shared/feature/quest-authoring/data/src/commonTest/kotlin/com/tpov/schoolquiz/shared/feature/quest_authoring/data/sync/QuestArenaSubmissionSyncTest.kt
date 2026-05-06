package com.tpov.schoolquiz.shared.feature.quest_authoring.data.sync

import com.tpov.schoolquiz.shared.core.persistence.DraftLessonEntity
import com.tpov.schoolquiz.shared.core.persistence.DraftQuestionEntity
import com.tpov.schoolquiz.shared.core.persistence.DraftSectionEntity
import com.tpov.schoolquiz.shared.core.persistence.DraftThemeEntity
import com.tpov.schoolquiz.shared.core.persistence.QuestArenaSubmissionEntity
import com.tpov.schoolquiz.shared.core.persistence.QuestDraftEntity
import com.tpov.schoolquiz.shared.core.persistence.QuestDraftSummaryEntity
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.QuestAuthoringEntityBundle
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.QuestAuthoringLocalDataSource
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.PrivateQuestSnapshot
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.QuestArenaSubmissionRemoteDataSource
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.QuestArenaSubmissionRequest
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.provider.QuestAuthoringTimestampProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QuestArenaSubmissionSyncTest {
    @Test
    fun sync_sendsPendingSubmissionWithDraftSnapshotAndMarksSent() = runTest {
        val local = FakeLocal(
            draft = entityBundle(),
            pending =
                mutableListOf(
                    QuestArenaSubmissionEntity(
                        id = "submission-1",
                        draftId = "draft-1",
                        ownerUid = "owner-1",
                        localRevision = 2L,
                        requestedAtMs = 10L,
                        lessonIds = listOf("lesson-1"),
                        targetShelf = "arena",
                        attemptCount = 0,
                        lastError = null,
                    ),
                ),
        )
        val remote = FakeRemote()
        val sync = QuestArenaSubmissionSync(local, remote, FakeClock(20L))

        val result = sync.sync()

        assertTrue(result.isSuccess)
        assertEquals("submission-1", remote.requests.single().submissionId)
        assertEquals("Draft", remote.requests.single().draft.title)
        assertEquals(7, remote.requests.single().questions.single().languageLevel)
        assertEquals(mapOf("ru" to 7), remote.requests.single().review.translatedLanguages)
        assertEquals(setOf("lesson-1"), remote.requests.single().targetLessonIds)
        assertEquals(listOf("submission-1"), local.sentIds)
    }

    @Test
    fun sync_whenRemoteFails_marksFailureAndReturnsFailure() = runTest {
        val local = FakeLocal(
            draft = entityBundle(),
            pending =
                mutableListOf(
                    QuestArenaSubmissionEntity(
                        id = "submission-1",
                        draftId = "draft-1",
                        ownerUid = "owner-1",
                        localRevision = 2L,
                        requestedAtMs = 10L,
                        lessonIds = listOf("lesson-1"),
                        targetShelf = "arena",
                        attemptCount = 0,
                        lastError = null,
                    ),
                ),
        )
        val remote = FakeRemote(failure = IllegalStateException("network"))
        val sync = QuestArenaSubmissionSync(local, remote, FakeClock(20L))

        val result = sync.sync()

        assertTrue(result.isFailure)
        assertEquals("network", local.failures.single().message)
    }

    private class FakeLocal(
        private val draft: QuestAuthoringEntityBundle?,
        private val pending: MutableList<QuestArenaSubmissionEntity>,
    ) : QuestAuthoringLocalDataSource {
        val sentIds = mutableListOf<String>()
        val failures = mutableListOf<Failure>()

        override fun observeDraftSummaries(ownerUid: String): Flow<List<QuestDraftSummaryEntity>> =
            flowOf(emptyList())

        override fun observeDraft(draftId: String): Flow<QuestAuthoringEntityBundle?> =
            flowOf(draft)

        override suspend fun getDraft(draftId: String): QuestAuthoringEntityBundle? =
            draft?.takeIf { it.draft.id == draftId }

        override suspend fun getActiveDraft(ownerUid: String): QuestAuthoringEntityBundle? = draft

        override suspend fun saveDraft(bundle: QuestAuthoringEntityBundle) = Unit

        override suspend fun upsertQuestion(question: DraftQuestionEntity) = Unit

        override suspend fun queueArenaSubmission(submission: QuestArenaSubmissionEntity) {
            pending += submission
        }

        override suspend fun findPendingArenaSubmissions(limit: Int): List<QuestArenaSubmissionEntity> =
            pending.take(limit)

        override suspend fun markArenaSubmissionFailure(
            submissionId: String,
            message: String?,
        ) {
            failures += Failure(submissionId, message)
        }

        override suspend fun markArenaSubmissionSent(
            submissionId: String,
            draftId: String,
            updatedAtMs: Long,
        ) {
            sentIds += submissionId
            pending.removeAll { it.id == submissionId }
        }

        override suspend fun upsertSyncedPrivateQuest(snapshot: PrivateQuestSnapshot) = Unit

        override suspend fun setDraftStatus(
            draftId: String,
            status: String,
            updatedAtMs: Long,
        ) = Unit
    }

    private data class Failure(
        val submissionId: String,
        val message: String?,
    )

    private class FakeRemote(
        private val failure: Exception? = null,
    ) : QuestArenaSubmissionRemoteDataSource {
        val requests = mutableListOf<QuestArenaSubmissionRequest>()

        override suspend fun submit(request: QuestArenaSubmissionRequest) {
            failure?.let { throw it }
            requests += request
        }
    }

    private class FakeClock(
        private val now: Long,
    ) : QuestAuthoringTimestampProvider {
        override fun nowMs(): Long = now
    }

    private fun entityBundle(): QuestAuthoringEntityBundle =
        QuestAuthoringEntityBundle(
            draft =
                QuestDraftEntity(
                    id = "draft-1",
                    ownerUid = "owner-1",
                    catalogId = "catalog-1",
                    title = "Draft",
                    description = null,
                    defaultLanguage = "ru",
                    defaultDifficulty = "EASY",
                    status = "REVIEW_QUEUED",
                    localRevision = 2L,
                    serverRevision = null,
                    publicQuestId = null,
                    createdAtMs = 1L,
                    updatedAtMs = 2L,
                    isActive = true,
                ),
            sections = listOf(DraftSectionEntity("section-1", "draft-1", "Section", 0)),
            themes = listOf(DraftThemeEntity("theme-1", "draft-1", "section-1", "Theme", 0)),
            lessons = listOf(DraftLessonEntity("lesson-1", "draft-1", "theme-1", "Lesson", 0)),
            questions =
                listOf(
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
                        languageLevel = 7,
                    ),
                ),
        )
}
