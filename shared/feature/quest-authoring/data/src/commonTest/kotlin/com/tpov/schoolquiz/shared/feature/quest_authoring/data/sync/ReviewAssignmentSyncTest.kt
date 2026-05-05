package com.tpov.schoolquiz.shared.feature.quest_authoring.data.sync

import com.tpov.schoolquiz.shared.core.persistence.ReviewAssignmentEntity
import com.tpov.schoolquiz.shared.core.sync.InMemorySyncStateRepository
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
        syncState.setCursor(reviewAssignmentSyncCursorId("tester"), 10L)
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
        assertEquals(listOf(10L), remote.changeCursors)
        assertEquals(listOf(setOf("no-longer-allowed", "new-visible")), remote.detailRequestIds)
        assertEquals(
            listOf("still-visible", "new-visible"),
            local.findAssignments("tester").map { it.id },
        )
        assertEquals(30L, syncState.getCursor(reviewAssignmentSyncCursorId("tester")))
    }

    @Test
    fun sync_whenAssignmentChangeListIsEmpty_doesNotFetchAssignmentDetails() = runTest {
        val syncState = InMemorySyncStateRepository()
        val local = FakeAssignmentLocal(listOf(assignmentEntity("cached")))
        val remote = FakeAssignmentRemote(changes = emptyList(), assignments = emptyMap())
        val sync = ReviewAssignmentSync(local, remote, syncState) { "tester" }

        val result = sync.sync()

        assertTrue(result.isSuccess)
        assertEquals(listOf(0L), remote.changeCursors)
        assertEquals(emptyList(), remote.detailRequestIds)
        assertEquals(listOf("cached"), local.findAssignments("tester").map { it.id })
        assertEquals(0L, syncState.getCursor(reviewAssignmentSyncCursorId("tester")))
    }

    private class FakeAssignmentRemote(
        private val changes: List<ReviewAssignmentChangeDto>,
        private val assignments: Map<String, ReviewAssignmentDto>,
    ) : ReviewAssignmentRemoteDataSource {
        val changeCursors = mutableListOf<Long>()
        val detailRequestIds = mutableListOf<Set<String>>()

        override suspend fun fetchAssignmentChangesSince(cursorMs: Long): List<ReviewAssignmentChangeDto> {
            changeCursors += cursorMs
            return changes.filter { it.changedAtMs > cursorMs }
        }

        override suspend fun fetchByIds(ids: Set<String>): List<ReviewAssignmentDto> {
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
