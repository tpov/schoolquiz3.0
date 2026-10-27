package com.tpov.schoolquiz.shared.feature.quest_authoring.domain

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestReviewOutcomeSnapshot
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.QuestReviewOutcomeRepository
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.FetchQuestReviewOutcomesUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class FetchQuestReviewOutcomesUseCaseTest {

    private class FakeRepo(
        private val snapshots: List<QuestReviewOutcomeSnapshot>,
        private val fail: Boolean = false,
    ) : QuestReviewOutcomeRepository {
        val advanced = mutableListOf<String>()
        var fetchCount = 0

        override suspend fun fetch(
            submissionIds: Set<String>,
            legacyDraftIds: Set<String>,
        ): Result<List<QuestReviewOutcomeSnapshot>> {
            fetchCount += 1
            if (fail) return Result.failure(IllegalStateException("transport"))
            return Result.success(snapshots)
        }

        override suspend fun advance(submissionId: String): Result<Unit> {
            advanced.add(submissionId)
            return Result.success(Unit)
        }
    }

    private fun snapshot(id: String, ready: Boolean, draftId: String = "draft-$id") =
        QuestReviewOutcomeSnapshot(
            submissionId = id,
            draftId = draftId,
            status = QuestReviewOutcomeSnapshot.STATUS_UNDER_REVIEW,
            rejectionReason = null,
            failedQuestions = emptyList(),
            detectedLanguage = null,
            outcomeReady = ready,
        )

    @Test
    fun `a plain read passes through without advancing`() = runTest {
        val repo = FakeRepo(listOf(snapshot("s1", ready = false)))
        val snapshots = FetchQuestReviewOutcomesUseCase(repo)(emptySet(), setOf("draft-s1"))
        assertEquals(1, snapshots.size)
        assertEquals(0, repo.advanced.size)
        assertEquals(1, repo.fetchCount)
    }

    @Test
    fun `a ready outcome is advanced and the read repeats once`() = runTest {
        val repo = FakeRepo(listOf(snapshot("s1", ready = true), snapshot("s2", ready = false)))
        val snapshots = FetchQuestReviewOutcomesUseCase(repo)(emptySet(), setOf("draft-s1", "draft-s2"))
        assertEquals(listOf("s1"), repo.advanced)
        assertEquals(2, repo.fetchCount)
        assertEquals(2, snapshots.size)
    }

    @Test
    fun `a transport failure is an empty result, not a crash`() = runTest {
        val repo = FakeRepo(emptyList(), fail = true)
        val snapshots = FetchQuestReviewOutcomesUseCase(repo)(emptySet(), setOf("draft-s1"))
        assertTrue(snapshots.isEmpty())
    }
}
