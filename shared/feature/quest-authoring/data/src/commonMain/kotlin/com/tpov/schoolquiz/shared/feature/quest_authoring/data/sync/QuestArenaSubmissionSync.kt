package com.tpov.schoolquiz.shared.feature.quest_authoring.data.sync

import com.tpov.schoolquiz.shared.core.sync.Syncable
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.QuestAuthoringLocalDataSource
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.mapper.QuestArenaSubmissionRequestMapper
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.mapper.QuestAuthoringMapper.toDomain
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.QuestArenaSubmissionRemoteDataSource
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.provider.QuestAuthoringTimestampProvider
import kotlinx.coroutines.CancellationException

class QuestArenaSubmissionSync(
    private val local: QuestAuthoringLocalDataSource,
    private val remote: QuestArenaSubmissionRemoteDataSource,
    private val timestampProvider: QuestAuthoringTimestampProvider,
) : Syncable {
    override suspend fun sync(): Result<Unit> {
        return try {
            val pending = local.findPendingArenaSubmissions(PENDING_LIMIT)
            for (submissionEntity in pending) {
                val submission = submissionEntity.toDomain()
                val bundle = local.getDraft(submissionEntity.draftId)?.toDomain()
                if (bundle == null) {
                    val error = IllegalStateException("Draft ${submissionEntity.draftId} not found")
                    local.markArenaSubmissionFailure(submissionEntity.id, error.message)
                    return Result.failure(error)
                }

                try {
                    remote.submit(QuestArenaSubmissionRequestMapper.run { submission.toRequest(bundle) })
                    local.markArenaSubmissionSent(
                        submissionId = submissionEntity.id,
                        draftId = submissionEntity.draftId,
                        updatedAtMs = timestampProvider.nowMs(),
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    local.markArenaSubmissionFailure(submissionEntity.id, e.message)
                    return Result.failure(e)
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private companion object {
        const val PENDING_LIMIT = 20
    }
}
