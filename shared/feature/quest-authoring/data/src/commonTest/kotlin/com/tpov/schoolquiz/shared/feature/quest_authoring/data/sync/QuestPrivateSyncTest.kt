package com.tpov.schoolquiz.shared.feature.quest_authoring.data.sync

import com.tpov.schoolquiz.shared.core.persistence.DraftQuestionEntity
import com.tpov.schoolquiz.shared.core.persistence.QuestArenaSubmissionEntity
import com.tpov.schoolquiz.shared.core.persistence.QuestDraftSummaryEntity
import com.tpov.schoolquiz.shared.core.sync.InMemorySyncStateRepository
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.QuestAuthoringEntityBundle
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.QuestAuthoringLocalDataSource
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ArenaDraftDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ArenaReviewDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.PrivateQuestSnapshot
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.PrivateQuestSyncChange
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.QuestArenaSubmissionRequest
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.QuestPrivateRemoteDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QuestPrivateSyncTest {
    @Test
    fun sync_fetchesPrivateSnapshotsOnlyForChangedQuestIds() = runTest {
        val syncState = InMemorySyncStateRepository()
        syncState.setCursor(privateQuestSyncCursorId("owner-1"), 10L)
        val local = FakeLocal()
        val remote =
            FakePrivateRemote(
                changes =
                    listOf(
                        PrivateQuestSyncChange("math", "old-quest", changedAtMs = 5L),
                        PrivateQuestSyncChange("math", "changed-quest", changedAtMs = 20L),
                    ),
                snapshots =
                    mapOf(
                        "old-quest" to snapshot("old-quest", changedAtMs = 5L),
                        "changed-quest" to snapshot("changed-quest", changedAtMs = 20L),
                    ),
            )
        val sync = QuestPrivateSync(local, remote, syncState) { "owner-1" }

        val result = sync.sync()

        assertTrue(result.isSuccess)
        assertEquals(listOf(10L), remote.changeCursors)
        assertEquals(listOf(listOf("changed-quest")), remote.snapshotRequestQuestIds)
        assertEquals(listOf("changed-quest"), local.synced.map { it.request.draft.id })
        assertEquals(20L, syncState.getCursor(privateQuestSyncCursorId("owner-1")))
    }

    @Test
    fun sync_whenPrivateChangeListIsEmpty_doesNotFetchSnapshots() = runTest {
        val syncState = InMemorySyncStateRepository()
        val local = FakeLocal()
        val remote = FakePrivateRemote(changes = emptyList(), snapshots = emptyMap())
        val sync = QuestPrivateSync(local, remote, syncState) { "owner-1" }

        val result = sync.sync()

        assertTrue(result.isSuccess)
        assertEquals(listOf(0L), remote.changeCursors)
        assertEquals(emptyList(), remote.snapshotRequestQuestIds)
        assertEquals(emptyList(), local.synced)
        assertEquals(0L, syncState.getCursor(privateQuestSyncCursorId("owner-1")))
    }

    private class FakePrivateRemote(
        private val changes: List<PrivateQuestSyncChange>,
        private val snapshots: Map<String, PrivateQuestSnapshot>,
    ) : QuestPrivateRemoteDataSource {
        val changeCursors = mutableListOf<Long>()
        val snapshotRequestQuestIds = mutableListOf<List<String>>()

        override suspend fun fetchChangedSince(cursorMs: Long): List<PrivateQuestSyncChange> {
            changeCursors += cursorMs
            return changes.filter { it.changedAtMs > cursorMs }
        }

        override suspend fun fetchSnapshots(changes: List<PrivateQuestSyncChange>): List<PrivateQuestSnapshot> {
            snapshotRequestQuestIds += changes.map { it.questId }
            return changes.mapNotNull { snapshots[it.questId] }
        }
    }

    private class FakeLocal : QuestAuthoringLocalDataSource {
        val rejections = mutableListOf<Pair<String, String?>>()

        override suspend fun findOwnerUidsAwaitingReview(): Set<String> = awaitingReviewOwners

        var awaitingReviewOwners: Set<String> = emptySet()

        override suspend fun applyRejection(
            draftId: String,
            reason: String?,
            updatedAtMs: Long,
        ) {
            rejections += draftId to reason
        }

        val synced = mutableListOf<PrivateQuestSnapshot>()

        override fun observeDraftSummaries(ownerUid: String): Flow<List<QuestDraftSummaryEntity>> =
            flowOf(emptyList())

        override fun observeDraft(draftId: String): Flow<QuestAuthoringEntityBundle?> =
            flowOf(null)

        override suspend fun getDraft(draftId: String): QuestAuthoringEntityBundle? = null

        override suspend fun getActiveDraft(ownerUid: String): QuestAuthoringEntityBundle? = null

        override suspend fun saveDraft(bundle: QuestAuthoringEntityBundle) = Unit

        override suspend fun upsertQuestion(question: DraftQuestionEntity) = Unit

        override suspend fun queueArenaSubmission(submission: QuestArenaSubmissionEntity) = Unit

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

        override suspend fun upsertSyncedPrivateQuest(snapshot: PrivateQuestSnapshot) {
            synced += snapshot
        }

        override suspend fun setDraftStatus(
            draftId: String,
            status: String,
            updatedAtMs: Long,
        ) = Unit
    }

    private fun snapshot(
        questId: String,
        changedAtMs: Long,
    ): PrivateQuestSnapshot =
        PrivateQuestSnapshot(
            serverRevision = changedAtMs,
            changedAtMs = changedAtMs,
            request =
                QuestArenaSubmissionRequest(
                    submissionId = "submission-$questId",
                    draftId = questId,
                    ownerUid = "owner-1",
                    localRevision = changedAtMs,
                    requestedAtMs = changedAtMs,
                    draft =
                        ArenaDraftDto(
                            id = questId,
                            catalogId = "math",
                            title = questId,
                            description = null,
                            defaultLanguage = "ru",
                            defaultDifficulty = "EASY",
                            publicQuestId = null,
                            createdAtMs = 1L,
                            updatedAtMs = changedAtMs,
                        ),
                    sections = emptyList(),
                    themes = emptyList(),
                    lessons = emptyList(),
                    questions = emptyList(),
                    review = ArenaReviewDto(),
                ),
        )
}
