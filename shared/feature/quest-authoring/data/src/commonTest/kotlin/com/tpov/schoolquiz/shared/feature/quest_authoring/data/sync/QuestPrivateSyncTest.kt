package com.tpov.schoolquiz.shared.feature.quest_authoring.data.sync

import com.tpov.schoolquiz.shared.core.persistence.DraftQuestionEntity
import com.tpov.schoolquiz.shared.core.persistence.OutboxEntity
import com.tpov.schoolquiz.shared.core.persistence.QuestDraftSummaryEntity
import com.tpov.schoolquiz.shared.core.sync.InMemorySyncStateRepository
import com.tpov.schoolquiz.shared.core.sync.JournalReadPolicy
import com.tpov.schoolquiz.shared.core.sync.SyncJournals
import com.tpov.schoolquiz.shared.core.sync.SYNC_PAGE_SIZE
import com.tpov.schoolquiz.shared.core.sync.SyncChangePage
import com.tpov.schoolquiz.shared.core.sync.SyncCursor
import com.tpov.schoolquiz.shared.core.sync.journalPage
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.QuestAuthoringEntityBundle
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.QuestAuthoringLocalDataSource
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.ArenaReviewSubmissionTarget
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ArenaDraftDto
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
        syncState.setCursor(privateQuestSyncCursorId("owner-1"), SyncCursor(10L, "math_old-quest"))
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
        assertEquals(listOf(SyncCursor(10L, "math_old-quest")), remote.changeCursors)
        assertEquals(listOf(listOf("changed-quest")), remote.snapshotRequestQuestIds)
        assertEquals(listOf("changed-quest"), local.synced.map { it.request.draft.id })
        assertEquals(SyncCursor(20L, "math_changed-quest"), syncState.getCursor(privateQuestSyncCursorId("owner-1")))
    }

    @Test
    fun sync_whenPrivateChangeListIsEmpty_doesNotFetchSnapshots() = runTest {
        val syncState = InMemorySyncStateRepository()
        val local = FakeLocal()
        val remote = FakePrivateRemote(changes = emptyList(), snapshots = emptyMap())
        val sync = QuestPrivateSync(local, remote, syncState) { "owner-1" }

        val result = sync.sync()

        assertTrue(result.isSuccess)
        assertEquals(listOf(SyncCursor.BEGINNING), remote.changeCursors)
        assertEquals(emptyList(), remote.snapshotRequestQuestIds)
        assertEquals(emptyList(), local.synced)
        assertEquals(SyncCursor.BEGINNING, syncState.getCursor(privateQuestSyncCursorId("owner-1")))
    }

    @Test
    fun sync_readsTwoChangesWrittenInTheSameMillisecond_andTheNextRunReadsNeither() = runTest {
        // По одному числу вторая запись той же миллисекунды пропадала без следа (AD-31).
        val syncState = InMemorySyncStateRepository()
        val local = FakeLocal()
        val remote =
            FakePrivateRemote(
                changes =
                    listOf(
                        PrivateQuestSyncChange("math", "quest-a", changedAtMs = 20L),
                        PrivateQuestSyncChange("math", "quest-b", changedAtMs = 20L),
                    ),
                snapshots =
                    mapOf(
                        "quest-a" to snapshot("quest-a", changedAtMs = 20L),
                        "quest-b" to snapshot("quest-b", changedAtMs = 20L),
                    ),
            )
        val sync = QuestPrivateSync(local, remote, syncState) { "owner-1" }

        sync.sync()
        assertEquals(listOf(listOf("quest-a", "quest-b")), remote.snapshotRequestQuestIds)
        assertEquals(SyncCursor(20L, "math_quest-b"), syncState.getCursor(privateQuestSyncCursorId("owner-1")))

        sync.sync()

        assertEquals(1, remote.snapshotRequestQuestIds.size, "ни одна из двух не вернулась повторно")
        assertEquals(listOf(SyncCursor.BEGINNING, SyncCursor(20L, "math_quest-b")), remote.changeCursors)
    }

    @Test
    fun sync_continuesFromTheFirstOfTwoSameMillisecondChanges() = runTest {
        val syncState = InMemorySyncStateRepository()
        syncState.setCursor(privateQuestSyncCursorId("owner-1"), SyncCursor(20L, "math_quest-a"))
        val local = FakeLocal()
        val remote =
            FakePrivateRemote(
                changes =
                    listOf(
                        PrivateQuestSyncChange("math", "quest-a", changedAtMs = 20L),
                        PrivateQuestSyncChange("math", "quest-b", changedAtMs = 20L),
                    ),
                snapshots = mapOf("quest-b" to snapshot("quest-b", changedAtMs = 20L)),
            )

        QuestPrivateSync(local, remote, syncState) { "owner-1" }.sync()

        assertEquals(listOf(listOf("quest-b")), remote.snapshotRequestQuestIds)
    }

    @Test
    fun sync_readsTheJournalPageByPage_andKeepsTheCursorBeforeAFailedPage() = runTest {
        val syncState = InMemorySyncStateRepository()
        val local = FakeLocal()
        val remote =
            FakePrivateRemote(
                changes = (1L..3L).map { PrivateQuestSyncChange("math", "quest-$it", changedAtMs = it) },
                snapshots = (1L..3L).associate { "quest-$it" to snapshot("quest-$it", changedAtMs = it) },
                pageSize = 2,
            )
        val sync = QuestPrivateSync(local, remote, syncState) { "owner-1" }

        val result = sync.sync()

        assertTrue(result.isSuccess)
        assertEquals(listOf(SyncCursor.BEGINNING, SyncCursor(2L, "math_quest-2")), remote.changeCursors)
        assertEquals(listOf("quest-1", "quest-2", "quest-3"), local.synced.map { it.request.draft.id })
        assertEquals(SyncCursor(3L, "math_quest-3"), syncState.getCursor(privateQuestSyncCursorId("owner-1")))
    }

    @Test
    fun sync_whenSnapshotFetchFails_leavesTheCursorWhereItWas() = runTest {
        val syncState = InMemorySyncStateRepository()
        syncState.setCursor(privateQuestSyncCursorId("owner-1"), SyncCursor(5L, "math_seen"))
        val remote =
            FakePrivateRemote(
                changes = listOf(PrivateQuestSyncChange("math", "quest-1", changedAtMs = 10L)),
                snapshots = emptyMap(),
                snapshotFailure = IllegalStateException("network"),
            )

        val result = QuestPrivateSync(FakeLocal(), remote, syncState) { "owner-1" }.sync()

        assertTrue(result.isFailure)
        assertEquals(SyncCursor(5L, "math_seen"), syncState.getCursor(privateQuestSyncCursorId("owner-1")))
    }

    @Test
    fun sync_countsAnUnreadableJournalRecordInsteadOfDroppingIt_andPassesIt() = runTest {
        val syncState = InMemorySyncStateRepository()
        val remote =
            FakePrivateRemote(
                changes = listOf(PrivateQuestSyncChange("math", "quest-1", changedAtMs = 10L)),
                snapshots = mapOf("quest-1" to snapshot("quest-1", changedAtMs = 10L)),
                unreadableDocIds = listOf("legacy" to 11L),
            )

        val result = QuestPrivateSync(FakeLocal(), remote, syncState) { "owner-1" }.sync()

        assertTrue(result.isSuccess)
        // Курсор прошёл и непонятую запись: иначе она читалась бы каждый проход.
        assertEquals(SyncCursor(11L, "legacy"), syncState.getCursor(privateQuestSyncCursorId("owner-1")))
    }


    @Test
    fun sync_reportsHowManyJournalRecordsItCouldNotRead() = runTest {
        // Число выбрасывалось `.map { }`, и приватный журнал молчал так же, как до AD-11: автор не
        // мог узнать, что о его квесте сказали, а прочитать не смогли.
        val syncState = InMemorySyncStateRepository()
        val status = RecordingSyncStatus()
        val remote =
            FakePrivateRemote(
                changes = listOf(PrivateQuestSyncChange("math", "q-1", changedAtMs = 20L)),
                snapshots = mapOf("q-1" to snapshot("q-1", changedAtMs = 20L)),
                unreadableDocIds = listOf("legacy-1" to 10L, "legacy-2" to 11L),
            )
        val sync = QuestPrivateSync(FakeLocal(), remote, syncState, status = status) { "owner-1" }

        val result = sync.sync()

        assertTrue(result.isSuccess)
        assertEquals(2, status.byJournal[SyncJournals.PRIVATE_QUESTS])
    }

    @Test
    fun sync_afterBackfill_stopsOnAnUnreadableRecordInsteadOfPassingIt() = runTest {
        val syncState = InMemorySyncStateRepository()
        val remote =
            FakePrivateRemote(
                changes = emptyList(),
                snapshots = emptyMap(),
                unreadableDocIds = listOf("legacy-1" to 10L),
            )
        val sync =
            QuestPrivateSync(
                FakeLocal(),
                remote,
                syncState,
                status = RecordingSyncStatus(),
                policy = JournalReadPolicy.STRICT,
            ) { "owner-1" }

        val result = sync.sync()

        assertTrue(result.isFailure, "после приведения журнала непонятой записи взяться неоткуда")
        assertEquals(SyncCursor.BEGINNING, syncState.getCursor(privateQuestSyncCursorId("owner-1")))
    }

    /**
     * Журнал приватных квестов в памяти: id документа — `{catalogId}_{questId}`, как пишет сервер;
     * страница строго после курсора-пары, как в Firestore.
     */
    private class FakePrivateRemote(
        private val changes: List<PrivateQuestSyncChange>,
        private val snapshots: Map<String, PrivateQuestSnapshot>,
        private val pageSize: Int = SYNC_PAGE_SIZE,
        private val snapshotFailure: Throwable? = null,
        private val unreadableDocIds: List<Pair<String, Long>> = emptyList(),
    ) : QuestPrivateRemoteDataSource {
        val changeCursors = mutableListOf<SyncCursor>()
        val snapshotRequestQuestIds = mutableListOf<List<String>>()

        private data class Doc(val cursor: SyncCursor, val change: PrivateQuestSyncChange?)

        override suspend fun fetchPage(
            cursor: SyncCursor,
            limit: Int,
        ): SyncChangePage<PrivateQuestSyncChange> {
            changeCursors += cursor
            val size = minOf(limit, pageSize)
            val docs =
                changes.map { Doc(SyncCursor(it.changedAtMs, "${it.catalogId}_${it.questId}"), it) } +
                    unreadableDocIds.map { (id, ms) -> Doc(SyncCursor(ms, id), null) }
            val slice = docs.sortedBy { it.cursor }.filter { it.cursor > cursor }.take(size)
            return journalPage(slice, size, { it.cursor }, { it.change })
        }

        override suspend fun fetchSnapshots(changes: List<PrivateQuestSyncChange>): List<PrivateQuestSnapshot> {
            snapshotFailure?.let { throw it }
            snapshotRequestQuestIds += changes.map { it.questId }
            return changes.mapNotNull { snapshots[it.questId] }
        }
    }

    private class FakeLocal : QuestAuthoringLocalDataSource {
        val rejections = mutableListOf<Pair<String, String?>>()

        override suspend fun findAwaitingReviewTargets(ownerUid: String): List<ArenaReviewSubmissionTarget> = emptyList()

        override suspend fun applyPublication(
            ownerUid: String,
            draftId: String,
            submissionId: String,
            updatedAtMs: Long,
        ): Boolean = false

        override suspend fun applyRejection(
            ownerUid: String,
            draftId: String,
            submissionId: String,
            reason: String?,
            updatedAtMs: Long,
        ): Boolean {
            rejections += draftId to reason
            return false
        }

        override suspend fun claimLegacyReviewSubmission(
            ownerUid: String,
            draftId: String,
            submissionId: String,
        ): Boolean = false

        val synced = mutableListOf<PrivateQuestSnapshot>()

        override fun observeDraftSummaries(ownerUid: String): Flow<List<QuestDraftSummaryEntity>> =
            flowOf(emptyList())

        override fun observeDraft(draftId: String): Flow<QuestAuthoringEntityBundle?> =
            flowOf(null)

        override suspend fun getDraft(draftId: String): QuestAuthoringEntityBundle? = null

        override suspend fun getActiveDraft(ownerUid: String): QuestAuthoringEntityBundle? = null

        var activatedDraftId: String? = null

        override suspend fun activateDraft(
            ownerUid: String,
            draftId: String,
        ) {
            activatedDraftId = draftId
        }

        override suspend fun saveDraft(bundle: QuestAuthoringEntityBundle) = Unit

        override suspend fun upsertQuestion(question: DraftQuestionEntity) = Unit

        override suspend fun queueArenaSubmission(
            outboxRow: OutboxEntity,
            draftId: String,
            submissionId: String,
            requestedAtMs: Long,
        ) = Unit

        override suspend fun upsertSyncedPrivateQuest(snapshot: PrivateQuestSnapshot) {
            synced += snapshot
        }

        override suspend fun setDraftStatus(
            draftId: String,
            status: String,
            updatedAtMs: Long,
        ) = Unit

        var resolvedConflict: OutboxEntity? = null

        override suspend fun resolveArenaConflict(
            conflictedRowId: Long,
            replacement: OutboxEntity,
            draftId: String,
            updatedAtMs: Long,
        ) {
            resolvedConflict = replacement
        }
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
                    expectedVersion = changedAtMs,
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
                ),
        )
}
