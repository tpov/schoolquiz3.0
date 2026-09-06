package com.tpov.schoolquiz.shared.feature.quest_authoring.data.sync

import com.tpov.schoolquiz.shared.core.persistence.ReviewAssignmentEntity
import com.tpov.schoolquiz.shared.core.sync.InMemorySyncStateRepository
import com.tpov.schoolquiz.shared.core.sync.JournalReadPolicy
import com.tpov.schoolquiz.shared.core.sync.SyncJournals
import com.tpov.schoolquiz.shared.core.sync.SYNC_PAGE_SIZE
import com.tpov.schoolquiz.shared.core.sync.SyncChangePage
import com.tpov.schoolquiz.shared.core.sync.SyncCursor
import com.tpov.schoolquiz.shared.core.sync.journalPage
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.ReviewAssignmentLocalDataSource
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ArenaReviewDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ReviewAssignmentChangeDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ReviewAssignmentDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ReviewAssignmentRemoteDataSource
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.SubmitReviewActionDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReviewAssignmentSyncTest {
    @Test
    fun sync_fetchesAssignmentDetailsByChangedIdsAndRemovesNoLongerAvailableOnes() = runTest {
        val syncState = InMemorySyncStateRepository()
        syncState.setCursor(reviewAssignmentSyncCursorId("tester"), SyncCursor(10L, "ignored-old"))
        val local =
            FakeAssignmentLocal(
                listOf(
                    assignmentEntity("still-visible"),
                    assignmentEntity("no-longer-allowed"),
                ),
            )
        val remote =
            FakeAssignmentRemote(
                changes =
                    listOf(
                        ReviewAssignmentChangeDto("ignored-old", changedAtMs = 5L),
                        ReviewAssignmentChangeDto("no-longer-allowed", changedAtMs = 20L),
                        ReviewAssignmentChangeDto("new-visible", changedAtMs = 30L),
                    ),
                assignments =
                    mapOf(
                        "new-visible" to assignmentDto("new-visible"),
                    ),
            )
        val sync = ReviewAssignmentSync(local, remote, syncState) { "tester" }

        val result = sync.sync()

        assertTrue(result.isSuccess)
        assertEquals(listOf(SyncCursor(10L, "ignored-old")), remote.changeCursors)
        assertEquals(listOf(setOf("no-longer-allowed", "new-visible")), remote.detailRequestIds)
        assertEquals(
            listOf("still-visible", "new-visible"),
            local.findAssignments("tester").map { it.id },
        )
        assertEquals(SyncCursor(30L, "new-visible"), syncState.getCursor(reviewAssignmentSyncCursorId("tester")))
    }

    @Test
    fun sync_whenAssignmentChangeListIsEmpty_doesNotFetchAssignmentDetails() = runTest {
        val syncState = InMemorySyncStateRepository()
        val local = FakeAssignmentLocal(listOf(assignmentEntity("cached")))
        val remote = FakeAssignmentRemote(changes = emptyList(), assignments = emptyMap())
        val sync = ReviewAssignmentSync(local, remote, syncState) { "tester" }

        val result = sync.sync()

        assertTrue(result.isSuccess)
        assertEquals(listOf(SyncCursor.BEGINNING), remote.changeCursors)
        assertEquals(emptyList(), remote.detailRequestIds)
        assertEquals(listOf("cached"), local.findAssignments("tester").map { it.id })
        assertEquals(SyncCursor.BEGINNING, syncState.getCursor(reviewAssignmentSyncCursorId("tester")))
    }

    @Test
    fun sync_readsTwoChangesWrittenInTheSameMillisecond_andTheNextRunReadsNeither() = runTest {
        val syncState = InMemorySyncStateRepository()
        val local = FakeAssignmentLocal(emptyList())
        val remote =
            FakeAssignmentRemote(
                changes =
                    listOf(
                        ReviewAssignmentChangeDto("a-1", changedAtMs = 20L),
                        ReviewAssignmentChangeDto("a-2", changedAtMs = 20L),
                    ),
                assignments = mapOf("a-1" to assignmentDto("a-1"), "a-2" to assignmentDto("a-2")),
            )
        val sync = ReviewAssignmentSync(local, remote, syncState) { "tester" }

        sync.sync()
        assertEquals(listOf(setOf("a-1", "a-2")), remote.detailRequestIds)
        assertEquals(SyncCursor(20L, "a-2"), syncState.getCursor(reviewAssignmentSyncCursorId("tester")))

        sync.sync()

        assertEquals(1, remote.detailRequestIds.size, "ни одна из двух не вернулась повторно")
    }

    @Test
    fun sync_continuesFromTheFirstOfTwoSameMillisecondChanges() = runTest {
        val syncState = InMemorySyncStateRepository()
        syncState.setCursor(reviewAssignmentSyncCursorId("tester"), SyncCursor(20L, "a-1"))
        val remote =
            FakeAssignmentRemote(
                changes =
                    listOf(
                        ReviewAssignmentChangeDto("a-1", changedAtMs = 20L),
                        ReviewAssignmentChangeDto("a-2", changedAtMs = 20L),
                    ),
                assignments = mapOf("a-2" to assignmentDto("a-2")),
            )

        ReviewAssignmentSync(FakeAssignmentLocal(emptyList()), remote, syncState) { "tester" }.sync()

        assertEquals(listOf(setOf("a-2")), remote.detailRequestIds)
    }

    @Test
    fun sync_readsTheJournalPageByPage_andTheCursorFollowsEachPage() = runTest {
        val syncState = InMemorySyncStateRepository()
        val remote =
            FakeAssignmentRemote(
                changes = (1L..3L).map { ReviewAssignmentChangeDto("a-$it", changedAtMs = it) },
                assignments = (1L..3L).associate { "a-$it" to assignmentDto("a-$it") },
                pageSize = 2,
            )
        val local = FakeAssignmentLocal(emptyList())

        val result = ReviewAssignmentSync(local, remote, syncState) { "tester" }.sync()

        assertTrue(result.isSuccess)
        assertEquals(listOf(SyncCursor.BEGINNING, SyncCursor(2L, "a-2")), remote.changeCursors)
        assertEquals(listOf(setOf("a-1", "a-2"), setOf("a-3")), remote.detailRequestIds)
        assertEquals(SyncCursor(3L, "a-3"), syncState.getCursor(reviewAssignmentSyncCursorId("tester")))
    }

    @Test
    fun sync_whenDetailFetchFails_leavesTheCursorWhereItWas() = runTest {
        val syncState = InMemorySyncStateRepository()
        syncState.setCursor(reviewAssignmentSyncCursorId("tester"), SyncCursor(5L, "seen"))
        val remote =
            FakeAssignmentRemote(
                changes = listOf(ReviewAssignmentChangeDto("a-1", changedAtMs = 10L)),
                assignments = emptyMap(),
                detailFailure = IllegalStateException("network"),
            )

        val result = ReviewAssignmentSync(FakeAssignmentLocal(emptyList()), remote, syncState) { "tester" }.sync()

        assertTrue(result.isFailure)
        assertEquals(SyncCursor(5L, "seen"), syncState.getCursor(reviewAssignmentSyncCursorId("tester")))
    }

    @Test
    fun sync_countsAnUnreadableJournalRecordAndPassesIt() = runTest {
        val syncState = InMemorySyncStateRepository()
        val remote =
            FakeAssignmentRemote(
                changes = listOf(ReviewAssignmentChangeDto("a-1", changedAtMs = 10L)),
                assignments = mapOf("a-1" to assignmentDto("a-1")),
                unreadableDocIds = listOf("no-type-no-id" to 11L),
            )

        val result = ReviewAssignmentSync(FakeAssignmentLocal(emptyList()), remote, syncState) { "tester" }.sync()

        assertTrue(result.isSuccess)
        assertEquals(SyncCursor(11L, "no-type-no-id"), syncState.getCursor(reviewAssignmentSyncCursorId("tester")))
    }


    @Test
    fun sync_reportsHowManyJournalRecordsItCouldNotRead() = runTest {
        // Для рецензента молчание значило «новых заданий нет». Число — единственный след (AD-11).
        val syncState = InMemorySyncStateRepository()
        val status = RecordingSyncStatus()
        val remote =
            FakeAssignmentRemote(
                changes = emptyList(),
                assignments = emptyMap(),
                unreadableDocIds = listOf("legacy-1" to 10L, "legacy-2" to 11L, "legacy-3" to 12L),
            )
        val sync = ReviewAssignmentSync(FakeAssignmentLocal(emptyList()), remote, syncState, status = status) { "tester" }

        val result = sync.sync()

        assertTrue(result.isSuccess)
        assertEquals(3, status.byJournal[SyncJournals.REVIEW_ASSIGNMENTS])
    }

    @Test
    fun sync_afterBackfill_stopsOnAnUnreadableRecordInsteadOfPassingIt() = runTest {
        val syncState = InMemorySyncStateRepository()
        val remote =
            FakeAssignmentRemote(
                changes = emptyList(),
                assignments = emptyMap(),
                unreadableDocIds = listOf("legacy-1" to 10L),
            )
        val sync =
            ReviewAssignmentSync(
                FakeAssignmentLocal(emptyList()),
                remote,
                syncState,
                status = RecordingSyncStatus(),
                policy = JournalReadPolicy.STRICT,
            ) { "tester" }

        val result = sync.sync()

        assertTrue(result.isFailure)
        assertEquals(SyncCursor.BEGINNING, syncState.getCursor(reviewAssignmentSyncCursorId("tester")))
    }

    /** Журнал ревью в памяти: id документа — id задания, как пишет сервер; страница после пары. */
    private class FakeAssignmentRemote(
        private val changes: List<ReviewAssignmentChangeDto>,
        private val assignments: Map<String, ReviewAssignmentDto>,
        private val pageSize: Int = SYNC_PAGE_SIZE,
        private val detailFailure: Throwable? = null,
        private val unreadableDocIds: List<Pair<String, Long>> = emptyList(),
    ) : ReviewAssignmentRemoteDataSource {
        val changeCursors = mutableListOf<SyncCursor>()
        val detailRequestIds = mutableListOf<Set<String>>()

        private data class Doc(val cursor: SyncCursor, val change: ReviewAssignmentChangeDto?)

        override suspend fun fetchAssignmentChangePage(
            cursor: SyncCursor,
            limit: Int,
        ): SyncChangePage<ReviewAssignmentChangeDto> {
            changeCursors += cursor
            val size = minOf(limit, pageSize)
            val docs =
                changes.map { Doc(SyncCursor(it.changedAtMs, it.id), it) } +
                    unreadableDocIds.map { (id, ms) -> Doc(SyncCursor(ms, id), null) }
            val slice = docs.sortedBy { it.cursor }.filter { it.cursor > cursor }.take(size)
            return journalPage(slice, size, { it.cursor }, { it.change })
        }

        override suspend fun fetchByIds(ids: Set<String>): List<ReviewAssignmentDto> {
            detailFailure?.let { throw it }
            detailRequestIds += ids
            return ids.mapNotNull { assignments[it] }
        }

        override suspend fun submitReviewAction(action: SubmitReviewActionDto) = Unit
    }

    private class FakeAssignmentLocal(
        initial: List<ReviewAssignmentEntity>,
    ) : ReviewAssignmentLocalDataSource {
        private val assignmentsByOwner = linkedMapOf("tester" to initial)

        override fun observeAssignments(ownerUid: String): Flow<List<ReviewAssignmentEntity>> =
            flowOf(assignmentsByOwner[ownerUid].orEmpty())

        override suspend fun findAssignments(ownerUid: String): List<ReviewAssignmentEntity> =
            assignmentsByOwner[ownerUid].orEmpty()

        override suspend fun replaceAssignments(
            ownerUid: String,
            assignments: List<ReviewAssignmentEntity>,
        ) {
            assignmentsByOwner[ownerUid] = assignments
        }

        override suspend fun applyAssignmentChanges(
            ownerUid: String,
            changedIds: Set<String>,
            assignments: List<ReviewAssignmentEntity>,
        ) {
            val current = assignmentsByOwner[ownerUid].orEmpty()
            assignmentsByOwner[ownerUid] =
                (current.filterNot { it.id in changedIds } + assignments)
                    .sortedBy { it.createdAtMs }
        }
    }

    private fun assignmentDto(id: String): ReviewAssignmentDto =
        ReviewAssignmentDto(
            id = id,
            submissionId = "submission-$id",
            ownerUid = "owner-1",
            catalogId = "catalog-1",
            draftId = "draft-1",
            questId = "quest-1",
            lessonId = "lesson-1",
            title = id,
            createdAtMs = 20L,
            taskKinds = setOf("TESTING"),
            sourceLanguages = setOf("ru"),
            newTranslationLanguages = emptySet(),
            reviewLanguages = emptySet(),
            checks = ArenaReviewDto(),
            questions = emptyList(),
        )

    private fun assignmentEntity(id: String): ReviewAssignmentEntity =
        ReviewAssignmentEntity(
            id = id,
            ownerUid = "tester",
            submissionId = "submission-$id",
            catalogId = "catalog-1",
            draftId = "draft-1",
            questId = "quest-1",
            lessonId = "lesson-1",
            title = id,
            createdAtMs = 1L,
            taskKinds = listOf("TESTING"),
            sourceLanguages = listOf("ru"),
            newTranslationLanguages = emptyList(),
            reviewLanguages = emptyList(),
            isTested = false,
            testingScore = null,
            isLogicReviewed = false,
            logicScore = null,
            isTranslationReviewed = false,
            translationScore = null,
            translatedLanguages = emptyList(),
        )
}