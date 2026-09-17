package com.tpov.schoolquiz.shared.feature.quest_authoring.data.sync

import com.tpov.schoolquiz.shared.core.sync.JournalReadPolicy
import com.tpov.schoolquiz.shared.core.sync.SyncJournals
import com.tpov.schoolquiz.shared.core.sync.SyncStateRepository
import com.tpov.schoolquiz.shared.core.sync.SyncStatusRepository
import com.tpov.schoolquiz.shared.core.sync.Syncable
import com.tpov.schoolquiz.shared.core.sync.syncJournal
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.ReviewAssignmentLocalDataSource
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.mapper.QuestAuthoringMapper.toEntityWithQuestions
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ReviewAssignmentRemoteDataSource
import kotlinx.coroutines.CancellationException

/**
 * Задания на ревью: журнал `admin/review/sync_changes` читается страницами по общему циклу
 * (AD-31). Задание, которое сервер по изменившемуся id не вернул, снимается локально — это первый
 * из трёх способов удаления (AD-26), и других здесь нет.
 */
class ReviewAssignmentSync(
    private val local: ReviewAssignmentLocalDataSource,
    private val remote: ReviewAssignmentRemoteDataSource,
    private val syncStateRepo: SyncStateRepository,
    /**
     * Куда сообщить, сколько записей журнала проход не понял.
     *
     * Прежде число выбрасывалось `.map { }`. Для рецензента это значило: новых заданий не пришло,
     * и ни одно число об этом не говорило (AD-11).
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
                    cursorId = reviewAssignmentSyncCursorId(uid),
                    policy = policy,
                    fetch = { remote.fetchAssignmentChangePage(it) },
                    apply = { changes ->
                        val changedIds = changes.map { it.id }.toSet()
                        val assignments = remote.fetchByIds(changedIds).map { it.toEntityWithQuestions(uid) }
                        local.applyAssignmentDetailChanges(
                            ownerUid = uid,
                            changedIds = changedIds,
                            assignments = assignments,
                        )
                        Result.success(Unit)
                    },
                )
                .onSuccess { status?.recordUnreadableChanges(SyncJournals.REVIEW_ASSIGNMENTS, it) }
                .map { }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

fun reviewAssignmentSyncCursorId(ownerUid: String): String = "review_assignments:$ownerUid"
