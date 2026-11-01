package com.tpov.schoolquiz.android.feature.quest.presentation.fake

import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.sync.SyncRunKind
import com.tpov.schoolquiz.shared.core.sync.SyncStatus
import com.tpov.schoolquiz.shared.core.sync.SyncStatusRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Фейк состояния синхронизации для компонентовых тестов: стартует «синков не было» и меняет
 * `lastSuccessAtMs` по [recordSuccess] — ровно то, на что перечитывание исходов (4.16/4.20)
 * отвечает повторным чтением.
 */
class FakeSyncStatusRepository(
    initial: SyncStatus = SyncStatus(),
) : SyncStatusRepository {
    private val status = MutableStateFlow(initial)

    override fun observeStatus(): Flow<SyncStatus> = status

    override suspend fun recordSuccess(
        kind: SyncRunKind,
        atMs: Long,
    ) {
        status.value =
            when (kind) {
                SyncRunKind.CONTENT -> status.value.copy(lastSuccessAtMs = atMs)
                SyncRunKind.PROFILE -> status.value.copy(profileLastSuccessAtMs = atMs)
                SyncRunKind.RESYNC -> status.value.copy(resyncLastSuccessAtMs = atMs)
            }
    }

    override suspend fun recordFailure(
        kind: SyncRunKind,
        error: SyncError,
        atMs: Long,
    ) {
        // Время неудачного прогона исходы не перечитывает: серверной правды могло и не приехать.
    }

    override suspend fun recordUnreadableChanges(
        journal: String,
        count: Int,
    ) {
        // Непрочитанные записи журналов исходы заявки не касаются.
    }
}
