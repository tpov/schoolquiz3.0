package com.tpov.schoolquiz.shared.feature.quest_authoring.data.sync

import com.tpov.schoolquiz.shared.core.sync.SyncStateRepository
import com.tpov.schoolquiz.shared.core.sync.Syncable
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.QuestAuthoringLocalDataSource
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.QuestPrivateRemoteDataSource
import kotlinx.coroutines.CancellationException

class QuestPrivateSync(
    private val local: QuestAuthoringLocalDataSource,
    private val remote: QuestPrivateRemoteDataSource,
    private val syncStateRepo: SyncStateRepository,
    private val currentUidProvider: suspend () -> String?,
) : Syncable {
    override suspend fun sync(): Result<Unit> {
        return try {
            val uid = currentUidProvider() ?: return Result.success(Unit)
            val cursorId = privateQuestSyncCursorId(uid)
            val cursor = syncStateRepo.getCursor(cursorId)
            val changes = remote.fetchChangedSince(cursor)
                .filter { it.catalogId.isNotBlank() && it.questId.isNotBlank() }
            if (changes.isEmpty()) return Result.success(Unit)

            val snapshots = remote.fetchSnapshots(changes)
            for (snapshot in snapshots) {
                local.upsertSyncedPrivateQuest(snapshot)
            }
            syncStateRepo.setCursor(cursorId, changes.maxOf { it.changedAtMs })
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

fun privateQuestSyncCursorId(ownerUid: String): String = "private_quests:$ownerUid"
