package com.tpov.schoolquiz.shared.feature.quest_authoring.data.sync

import com.tpov.schoolquiz.shared.core.sync.SyncStateRepository
import com.tpov.schoolquiz.shared.core.sync.Syncable
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.ReviewAssignmentLocalDataSource
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.mapper.QuestAuthoringMapper.toEntityWithQuestions
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ReviewAssignmentRemoteDataSource
import kotlinx.coroutines.CancellationException

class ReviewAssignmentSync(
    private val local: ReviewAssignmentLocalDataSource,
    private val remote: ReviewAssignmentRemoteDataSource,
    private val syncStateRepo: SyncStateRepository,
    private val currentUidProvider: suspend () -> String?,
) : Syncable {
    override suspend fun sync(): Result<Unit> {
        return try {
            val uid = currentUidProvider() ?: return Result.success(Unit)
            val cursorId = reviewAssignmentSyncCursorId(uid)
            val cursor = syncStateRepo.getCursor(cursorId)
            val changes = remote.fetchAssignmentChangesSince(cursor)
                .filter { it.id.isNotBlank() }
            if (changes.isEmpty()) return Result.success(Unit)

            val changedIds = changes.map { it.id }.toSet()
            val assignments = remote.fetchByIds(changedIds).map { it.toEntityWithQuestions(uid) }
            local.applyAssignmentDetailChanges(
                ownerUid = uid,
                changedIds = changedIds,
                assignments = assignments,
            )
            syncStateRepo.setCursor(cursorId, changes.maxOf { it.changedAtMs })
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

fun reviewAssignmentSyncCursorId(ownerUid: String): String = "review_assignments:$ownerUid"
