package com.tpov.schoolquiz.android.feature.app_shell.presentation.fake

import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.sync.SyncRunKind
import com.tpov.schoolquiz.shared.core.sync.SyncStatus
import com.tpov.schoolquiz.shared.core.sync.SyncStatusRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Наблюдаемый снимок синхронизации без базы: тест сам решает, что репозиторий сейчас отдаёт. */
class FakeSyncStatusRepository(
    initial: SyncStatus = SyncStatus(),
) : SyncStatusRepository {
    private val state = MutableStateFlow(initial)

    override fun observeStatus(): Flow<SyncStatus> = state.asStateFlow()

    fun emit(status: SyncStatus) {
        state.value = status
    }

    override suspend fun recordSuccess(kind: SyncRunKind, atMs: Long) {
        state.value =
            when (kind) {
                SyncRunKind.CONTENT -> state.value.copy(lastSuccessAtMs = atMs, lastError = null)
                SyncRunKind.PROFILE ->
                    state.value.copy(profileLastSuccessAtMs = atMs, profileLastError = null)
                SyncRunKind.RESYNC ->
                    state.value.copy(resyncLastSuccessAtMs = atMs, resyncLastError = null)
            }
    }

    override suspend fun recordFailure(
        kind: SyncRunKind,
        error: SyncError,
        atMs: Long,
    ) {
        state.value =
            when (kind) {
                SyncRunKind.CONTENT -> state.value.copy(lastError = error)
                SyncRunKind.PROFILE -> state.value.copy(profileLastError = error)
                SyncRunKind.RESYNC -> state.value.copy(resyncLastError = error)
            }
    }

    private val unreadableByJournal = mutableMapOf<String, Int>()

    override suspend fun recordUnreadableChanges(journal: String, count: Int) {
        unreadableByJournal[journal] = count
        state.value = state.value.copy(unreadableChanges = unreadableByJournal.values.sum())
    }
}
