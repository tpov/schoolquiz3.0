package com.tpov.schoolquiz.shared.feature.quest_authoring.data.sync

import com.tpov.schoolquiz.shared.core.sync.JournalReadPolicy
import com.tpov.schoolquiz.shared.core.sync.SyncJournals
import com.tpov.schoolquiz.shared.core.sync.SyncStateRepository
import com.tpov.schoolquiz.shared.core.sync.SyncStatusRepository
import com.tpov.schoolquiz.shared.core.sync.Syncable
import com.tpov.schoolquiz.shared.core.sync.syncJournal
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.QuestAuthoringLocalDataSource
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.QuestPrivateRemoteDataSource
import kotlinx.coroutines.CancellationException

/**
 * Приватные квесты автора: журнал `private/{uid}/sync_changes` читается страницами по общему
 * циклу (AD-31), снимки изменившихся квестов подтягиваются и сливаются с локальным черновиком.
 */
class QuestPrivateSync(
    private val local: QuestAuthoringLocalDataSource,
    private val remote: QuestPrivateRemoteDataSource,
    private val syncStateRepo: SyncStateRepository,
    /**
     * Куда сообщить, сколько записей журнала проход не понял.
     *
     * Прежде число выбрасывалось `.map { }`, и приватный журнал молчал так же, как до AD-11:
     * автор не узнавал, что о его квесте сказали, а прочитать не смогли.
     */
    private val status: SyncStatusRepository? = null,
    /** Строгость разбора — общая на все четыре журнала (AD-11). */
    private val policy: JournalReadPolicy = JournalReadPolicy.TOLERANT,
    private val currentUidProvider: suspend () -> String?,
) : Syncable {
    override suspend fun sync(): Result<Unit> {
        return try {
            val uid = currentUidProvider() ?: return Result.success(Unit)
            syncStateRepo
                .syncJournal(
                    cursorId = privateQuestSyncCursorId(uid),
                    policy = policy,
                    fetch = { remote.fetchPage(it) },
                    apply = { changes ->
                        for (snapshot in remote.fetchSnapshots(changes)) {
                            local.upsertSyncedPrivateQuest(snapshot)
                        }
                        Result.success(Unit)
                    },
                )
                .onSuccess { status?.recordUnreadableChanges(SyncJournals.PRIVATE_QUESTS, it) }
                .map { }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

fun privateQuestSyncCursorId(ownerUid: String): String = "private_quests:$ownerUid"
