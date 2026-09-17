package com.tpov.schoolquiz.shared.feature.quest_authoring.data.outbox

import com.tpov.schoolquiz.shared.core.outbox.OutboxOperations
import com.tpov.schoolquiz.shared.core.outbox.OutboxRecord
import com.tpov.schoolquiz.shared.core.outbox.OutboxState
import com.tpov.schoolquiz.shared.core.persistence.DraftQuestionEntity
import com.tpov.schoolquiz.shared.core.persistence.OutboxEntity
import com.tpov.schoolquiz.shared.core.persistence.QuestDraftSummaryEntity
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.QuestAuthoringEntityBundle
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.QuestAuthoringLocalDataSource
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.PrivateQuestSnapshot
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.provider.QuestAuthoringTimestampProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Локальная половина заявки на арену — статус черновика `REVIEW_QUEUED`: он заперт на
 * редактирование, «потому что уже подан». Карантин терминален, заявка не уедет никогда, и
 * оставленный статус запер бы черновик навсегда (AD-28).
 */
class QuestArenaQuarantineRollbackTest {

    @Test
    fun quarantinedSubmission_unlocksTheDraftWithTheReason() = runTest {
        val local = FakeLocal(status = "REVIEW_QUEUED")

        // До
        assertEquals("REVIEW_QUEUED", local.status)
        assertEquals(null, local.rejectionReason)

        QuestArenaQuarantineRollback(local, FixedClock(77L)).onQuarantined(record())

        // После
        assertEquals("DRAFT", local.status, "черновик обязан снова стать редактируемым")
        assertEquals("Submission carries no questions", local.rejectionReason)
        assertEquals(77L, local.updatedAtMs)
    }

    @Test
    fun aRecordPointingAtNoDraft_changesNothing() = runTest {
        val local = FakeLocal(status = "REVIEW_QUEUED")

        QuestArenaQuarantineRollback(local, FixedClock(77L))
            .onQuarantined(record(entityRef = "lesson_runner:attempt:attempt-1"))

        assertEquals("REVIEW_QUEUED", local.status)
    }

    private fun record(entityRef: String? = QuestArenaOutboxWriter.draftRef(DRAFT_ID)) =
        OutboxRecord(
            id = 1L,
            mutationId = OutboxOperations.mutationKey(OutboxOperations.SUBMIT_ARENA, "submission-1"),
            ownerUid = "owner-1",
            operation = OutboxOperations.SUBMIT_ARENA,
            payload = """{"submissionId":"submission-1","draftId":"draft-1"}""",
            entityRef = entityRef,
            state = OutboxState.QUARANTINED,
            createdAtMs = 1L,
            attemptCount = 5,
            lastError = "Submission carries no questions",
        )

    private class FixedClock(private val nowMs: Long) : QuestAuthoringTimestampProvider {
        override fun nowMs(): Long = nowMs
    }

    /** Держит один черновик и его статус — ровно то, что откат обязан изменить. */
    private class FakeLocal(var status: String) : QuestAuthoringLocalDataSource {
        var rejectionReason: String? = null
        var updatedAtMs: Long = 0L

        override suspend fun applyPublication(
            draftId: String,
            updatedAtMs: Long,
        ) = Unit

        override suspend fun applyRejection(
            draftId: String,
            reason: String?,
            updatedAtMs: Long,
        ) {
            if (draftId != DRAFT_ID) return
            status = "DRAFT"
            rejectionReason = reason
            this.updatedAtMs = updatedAtMs
        }

        override fun observeDraftSummaries(ownerUid: String): Flow<List<QuestDraftSummaryEntity>> =
            flowOf(emptyList())

        override fun observeDraft(draftId: String): Flow<QuestAuthoringEntityBundle?> = flowOf(null)

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
            requestedAtMs: Long,
        ) = Unit

        override suspend fun findOwnerUidsAwaitingReview(): Set<String> = emptySet()

        override suspend fun upsertSyncedPrivateQuest(snapshot: PrivateQuestSnapshot) = Unit

        override suspend fun setDraftStatus(
            draftId: String,
            status: String,
            updatedAtMs: Long,
        ) {
            if (draftId == DRAFT_ID) this.status = status
        }

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

    private companion object {
        const val DRAFT_ID = "draft-1"
    }
}
