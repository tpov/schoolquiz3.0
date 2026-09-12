package com.tpov.schoolquiz.shared.feature.quest_authoring.data.sync

import com.tpov.schoolquiz.shared.core.persistence.DraftQuestionEntity
import com.tpov.schoolquiz.shared.core.persistence.OutboxEntity
import com.tpov.schoolquiz.shared.core.persistence.QuestDraftSummaryEntity
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.ArenaReviewSubmissionTarget
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.QuestAuthoringEntityBundle
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.QuestAuthoringLocalDataSource
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.PrivateQuestSnapshot
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.QuestArenaSubmissionRemoteDataSource
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.QuestSubmissionOutcome
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.QuestSubmissionOutcomeRequest
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.provider.QuestAuthoringTimestampProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class QuestArenaOutcomeSyncTest {
    @Test
    fun sync_readsOnlyTheCurrentAccountOnASharedDevice() = runTest {
        val local = FakeLocal().apply {
            targetsByOwner["author-a"] = listOf(ArenaReviewSubmissionTarget("draft-a", "submission-a"))
            targetsByOwner["author-b"] = listOf(ArenaReviewSubmissionTarget("draft-b", "submission-b"))
        }
        val remote = FakeRemote()

        val result = sync(local, remote) { "author-b" }.sync()

        assertTrue(result.isSuccess)
        assertEquals(listOf("author-b"), local.requestedOwners)
        assertEquals(
            listOf(QuestSubmissionOutcomeRequest(setOf("submission-b"), emptySet())),
            remote.requests,
        )
    }

    @Test
    fun staleTerminalOutcomeDoesNotSettleANewerResubmission() = runTest {
        val local = FakeLocal().apply {
            targetsByOwner[OWNER] = listOf(ArenaReviewSubmissionTarget(DRAFT, "submission-old"))
        }
        val remote =
            FakeRemote(
                outcomes = listOf(rejected("submission-old", DRAFT)),
                beforeReturn = {
                    local.targetsByOwner[OWNER] = listOf(ArenaReviewSubmissionTarget(DRAFT, "submission-new"))
                },
            )

        val result = sync(local, remote) { OWNER }.sync()

        assertTrue(result.isSuccess)
        assertEquals(emptyList(), local.rejections)
        assertEquals("submission-new", local.targetsByOwner.getValue(OWNER).single().activeSubmissionId)
    }

    @Test
    fun legacyAwaitingDraftClaimsTheServerSubmissionBeforeTheNextRead() = runTest {
        val local = FakeLocal().apply {
            targetsByOwner[OWNER] = listOf(ArenaReviewSubmissionTarget(DRAFT, activeSubmissionId = null))
        }
        val remote = FakeRemote(outcomes = listOf(underReview("submission-legacy", DRAFT)))

        val result = sync(local, remote) { OWNER }.sync()

        assertTrue(result.isSuccess)
        assertEquals(listOf(Claim(OWNER, DRAFT, "submission-legacy")), local.claims)
        assertEquals("submission-legacy", local.targetsByOwner.getValue(OWNER).single().activeSubmissionId)
        assertEquals(emptyList(), local.rejections)
        assertEquals(emptyList(), local.publications)
    }

    @Test
    fun unexpectedOutcomeFailsBeforeChangingAnyDraft() = runTest {
        val local = FakeLocal().apply {
            targetsByOwner[OWNER] = listOf(ArenaReviewSubmissionTarget(DRAFT, "submission-current"))
        }
        val remote = FakeRemote(outcomes = listOf(rejected("submission-other", "other-draft")))

        val result = sync(local, remote) { OWNER }.sync()

        assertTrue(result.isFailure)
        assertEquals(emptyList(), local.rejections)
        assertEquals(emptyList(), local.publications)
        assertEquals(emptyList(), local.claims)
    }

    @Test
    fun noAuthenticatedAccountDoesNotReadOrMutate() = runTest {
        val local = FakeLocal().apply {
            targetsByOwner[OWNER] = listOf(ArenaReviewSubmissionTarget(DRAFT, "submission-current"))
        }
        val remote = FakeRemote()

        val result = sync(local, remote) { null }.sync()

        assertTrue(result.isSuccess)
        assertEquals(emptyList(), local.requestedOwners)
        assertEquals(emptyList(), remote.requests)
    }

    @Test
    fun cancellationFromTheAccountBoundaryIsNotConvertedToAFailedSync() = runTest {
        try {
            sync(FakeLocal(), FakeRemote()) { throw CancellationException("stop") }.sync()
            fail("cancellation must leave the sync boundary")
        } catch (_: CancellationException) {
            // expected
        }
    }

    private fun sync(
        local: FakeLocal,
        remote: FakeRemote,
        uid: suspend () -> String?,
    ) =
        QuestArenaOutcomeSync(
            local = local,
            remote = remote,
            timestampProvider = FixedClock,
            currentUidProvider = uid,
        )

    private fun rejected(submissionId: String, draftId: String) =
        QuestSubmissionOutcome(
            submissionId = submissionId,
            draftId = draftId,
            status = QuestSubmissionOutcome.STATUS_REJECTED,
            rejectionReason = "Please correct the format",
        )

    private fun underReview(submissionId: String, draftId: String) =
        QuestSubmissionOutcome(
            submissionId = submissionId,
            draftId = draftId,
            status = "UNDER_REVIEW",
            rejectionReason = null,
        )

    private class FakeRemote(
        private val outcomes: List<QuestSubmissionOutcome> = emptyList(),
        private val beforeReturn: () -> Unit = {},
    ) : QuestArenaSubmissionRemoteDataSource {
        val requests = mutableListOf<QuestSubmissionOutcomeRequest>()

        override suspend fun fetchOutcomes(request: QuestSubmissionOutcomeRequest): List<QuestSubmissionOutcome> {
            requests += request
            beforeReturn()
            return outcomes
        }
    }

    private class FakeLocal : QuestAuthoringLocalDataSource {
        val targetsByOwner = mutableMapOf<String, List<ArenaReviewSubmissionTarget>>()
        val requestedOwners = mutableListOf<String>()
        val publications = mutableListOf<Settlement>()
        val rejections = mutableListOf<Settlement>()
        val claims = mutableListOf<Claim>()

        override suspend fun findAwaitingReviewTargets(ownerUid: String): List<ArenaReviewSubmissionTarget> {
            requestedOwners += ownerUid
            return targetsByOwner[ownerUid].orEmpty()
        }

        override suspend fun applyPublication(
            ownerUid: String,
            draftId: String,
            submissionId: String,
            updatedAtMs: Long,
        ): Boolean = settle(publications, ownerUid, draftId, submissionId)

        override suspend fun applyRejection(
            ownerUid: String,
            draftId: String,
            submissionId: String,
            reason: String?,
            updatedAtMs: Long,
        ): Boolean = settle(rejections, ownerUid, draftId, submissionId, reason)

        override suspend fun claimLegacyReviewSubmission(
            ownerUid: String,
            draftId: String,
            submissionId: String,
        ): Boolean {
            val targets = targetsByOwner[ownerUid].orEmpty()
            val target = targets.singleOrNull { it.draftId == draftId && it.activeSubmissionId == null } ?: return false
            targetsByOwner[ownerUid] = targets.map {
                if (it == target) it.copy(activeSubmissionId = submissionId) else it
            }
            claims += Claim(ownerUid, draftId, submissionId)
            return true
        }

        private fun settle(
            destination: MutableList<Settlement>,
            ownerUid: String,
            draftId: String,
            submissionId: String,
            reason: String? = null,
        ): Boolean {
            val targets = targetsByOwner[ownerUid].orEmpty()
            val target = targets.singleOrNull {
                it.draftId == draftId && (it.activeSubmissionId == submissionId || it.activeSubmissionId == null)
            } ?: return false
            targetsByOwner[ownerUid] = targets.filterNot { it == target }
            destination += Settlement(ownerUid, draftId, submissionId, reason)
            return true
        }

        override fun observeDraftSummaries(ownerUid: String): Flow<List<QuestDraftSummaryEntity>> = emptyFlow()

        override fun observeDraft(draftId: String): Flow<QuestAuthoringEntityBundle?> = emptyFlow()

        override suspend fun getDraft(draftId: String): QuestAuthoringEntityBundle? = null

        override suspend fun getActiveDraft(ownerUid: String): QuestAuthoringEntityBundle? = null

        override suspend fun activateDraft(ownerUid: String, draftId: String) = Unit

        override suspend fun saveDraft(bundle: QuestAuthoringEntityBundle) = Unit

        override suspend fun upsertQuestion(question: DraftQuestionEntity) = Unit

        override suspend fun queueArenaSubmission(
            outboxRow: OutboxEntity,
            draftId: String,
            submissionId: String,
            requestedAtMs: Long,
        ) = Unit

        override suspend fun upsertSyncedPrivateQuest(snapshot: PrivateQuestSnapshot) = Unit

        override suspend fun setDraftStatus(draftId: String, status: String, updatedAtMs: Long) = Unit

        override suspend fun resolveArenaConflict(
            conflictedRowId: Long,
            replacement: OutboxEntity,
            draftId: String,
            updatedAtMs: Long,
        ) = Unit
    }

    private data class Settlement(
        val ownerUid: String,
        val draftId: String,
        val submissionId: String,
        val reason: String?,
    )

    private data class Claim(
        val ownerUid: String,
        val draftId: String,
        val submissionId: String,
    )

    private object FixedClock : QuestAuthoringTimestampProvider {
        override fun nowMs(): Long = 1_000L
    }

    private companion object {
        const val OWNER = "author-1"
        const val DRAFT = "draft-1"
    }
}
