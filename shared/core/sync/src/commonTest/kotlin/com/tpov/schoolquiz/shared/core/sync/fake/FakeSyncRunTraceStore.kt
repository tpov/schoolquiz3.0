package com.tpov.schoolquiz.shared.core.sync.fake

import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.sync.SyncRunKind
import com.tpov.schoolquiz.shared.core.sync.SyncRunTrace
import com.tpov.schoolquiz.shared.core.sync.SyncRunTraceStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * След прогона в памяти теста — но с той же формой, что и в базе: строка на владельца и вид
 * прогона.
 *
 * Хранилище живёт снаружи репозитория намеренно: так тест может пережить его пересоздание — ровно
 * то, что делает перезапуск приложения.
 */
class FakeSyncRunTraceStore(
    private val byOwner: MutableStateFlow<Map<Key, SyncRunTrace>> = MutableStateFlow(emptyMap()),
) : SyncRunTraceStore {

    /** Ключ строки — тот же, что в схеме: владелец и вид прогона. */
    data class Key(val ownerUid: String, val kind: SyncRunKind)

    /** Кому писали в последний раз — чтобы тест видел ключ, а не только значение. */
    var lastWrittenOwner: String? = null
        private set

    fun set(ownerUid: String, kind: SyncRunKind, trace: SyncRunTrace) {
        byOwner.value = byOwner.value + (Key(ownerUid, kind) to trace)
    }

    fun traceOf(ownerUid: String, kind: SyncRunKind = SyncRunKind.CONTENT): SyncRunTrace =
        byOwner.value[Key(ownerUid, kind)] ?: SyncRunTrace()

    override fun observeTraces(ownerUid: String): Flow<Map<SyncRunKind, SyncRunTrace>> =
        byOwner.map { rows ->
            rows.filterKeys { it.ownerUid == ownerUid }.mapKeys { (key, _) -> key.kind }
        }

    override suspend fun recordSuccess(ownerUid: String, kind: SyncRunKind, atMs: Long) {
        lastWrittenOwner = ownerUid
        set(ownerUid, kind, SyncRunTrace(lastSuccessAtMs = atMs, lastError = null))
    }

    override suspend fun recordFailure(ownerUid: String, kind: SyncRunKind, error: SyncError) {
        lastWrittenOwner = ownerUid
        set(ownerUid, kind, traceOf(ownerUid, kind).copy(lastError = error))
    }
}
