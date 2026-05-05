package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.logic.validateArenaReadiness
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestArenaSubmission
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestArenaSubmissionId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraftId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraftStatus
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.provider.QuestAuthoringIdProvider
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.provider.QuestAuthoringTimestampProvider
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.QuestAuthoringRepository
import kotlinx.coroutines.CancellationException

class SubmitQuestDraftToArenaUseCase(
    private val repository: QuestAuthoringRepository,
    private val idProvider: QuestAuthoringIdProvider,
    private val timestampProvider: QuestAuthoringTimestampProvider,
) {
    suspend operator fun invoke(draftId: QuestDraftId): Result<QuestArenaSubmissionId> {
        return try {
            val bundle = requireNotNull(repository.getDraft(draftId)) {
                "Draft ${draftId.value} not found"
            }
            require(bundle.draft.status in SUBMITTABLE_STATUSES) {
                "Draft ${draftId.value} is already submitted or not editable"
            }
            val readiness = validateArenaReadiness(bundle)
            require(readiness.canSend) {
                "Draft ${draftId.value} is not ready for arena submission"
            }

            val submissionId = QuestArenaSubmissionId(idProvider.nextId("arena-submission"))
            val submission = QuestArenaSubmission(
                id = submissionId,
                draftId = draftId,
                ownerUid = bundle.draft.ownerUid,
                localRevision = bundle.draft.localRevision,
                requestedAtMs = timestampProvider.nowMs(),
            )
            repository.queueArenaSubmission(submission).getOrThrow()
            Result.success(submissionId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private companion object {
        val SUBMITTABLE_STATUSES =
            setOf(
                QuestDraftStatus.DRAFT,
                QuestDraftStatus.SYNC_PENDING,
                QuestDraftStatus.SYNCED_PRIVATE,
            )
    }
}
