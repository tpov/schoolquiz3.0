package com.tpov.schoolquiz.shared.feature.quest_authoring.data

import com.tpov.schoolquiz.shared.feature.quest_authoring.data.mapper.QuestAuthoringMapper.toDomain
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.mapper.QuestAuthoringMapper.toEntity
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.mapper.QuestAuthoringMapper.toEntityBundle
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftQuestion
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestArenaSubmission
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestAuthoringBundle
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraftId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraftStatus
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraftSummary
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.QuestAuthoringRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class QuestAuthoringRepositoryImpl(
    private val local: QuestAuthoringLocalDataSource,
) : QuestAuthoringRepository {

    override fun observeDraftSummaries(ownerUid: String): Flow<List<QuestDraftSummary>> =
        local.observeDraftSummaries(ownerUid).map { summaries ->
            summaries.map { it.toDomain() }
        }

    override fun observeDraft(draftId: QuestDraftId): Flow<QuestAuthoringBundle?> =
        local.observeDraft(draftId.value).map { it?.toDomain() }

    override suspend fun getDraft(draftId: QuestDraftId): QuestAuthoringBundle? =
        local.getDraft(draftId.value)?.toDomain()

    override suspend fun getActiveDraft(ownerUid: String): QuestAuthoringBundle? =
        local.getActiveDraft(ownerUid)?.toDomain()

    override suspend fun saveDraft(bundle: QuestAuthoringBundle): Result<Unit> =
        runCancellable {
            local.saveDraft(bundle.toEntityBundle())
        }

    override suspend fun upsertQuestion(question: DraftQuestion): Result<Unit> =
        runCancellable {
            local.upsertQuestion(question.toEntity())
        }

    override suspend fun queueArenaSubmission(submission: QuestArenaSubmission): Result<Unit> =
        runCancellable {
            local.queueArenaSubmission(submission.toEntity())
        }

    override suspend fun setDraftStatus(
        draftId: QuestDraftId,
        status: QuestDraftStatus,
        updatedAtMs: Long,
    ): Result<Unit> =
        runCancellable {
            local.setDraftStatus(
                draftId = draftId.value,
                status = status.name,
                updatedAtMs = updatedAtMs,
            )
        }

    private suspend fun runCancellable(block: suspend () -> Unit): Result<Unit> {
        return try {
            block()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
