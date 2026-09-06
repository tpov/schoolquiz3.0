package com.tpov.schoolquiz.shared.feature.internet.profile.data.outbox

import com.tpov.schoolquiz.shared.core.outbox.OutboxCounts
import com.tpov.schoolquiz.shared.core.outbox.OutboxDecision
import com.tpov.schoolquiz.shared.core.outbox.OutboxEntitySync
import com.tpov.schoolquiz.shared.core.outbox.OutboxRecord
import com.tpov.schoolquiz.shared.core.outbox.OutboxState
import com.tpov.schoolquiz.shared.core.outbox.OutboxStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.yield

/** Очередь в памяти — ровно то, что профилю нужно от неё: положить, найти по ссылке, наблюдать. */
class FakeOutboxStore : OutboxStore {
    private val records = MutableStateFlow<List<OutboxRecord>>(emptyList())
    private var nextId = 1L

    val all: List<OutboxRecord> get() = records.value

    /** Уступает поток перед выборкой по ссылке — чтобы тест мог столкнуть два постановщика. */
    var yieldBeforeFind = false

    fun seed(record: OutboxRecord) {
        records.value = records.value + record.copy(id = nextId++)
    }

    /** Переводит запись в состояние, как это сделал бы движок по решению политики. */
    fun setState(id: Long, state: OutboxState, lastError: String? = null) {
        records.value = records.value.map { if (it.id == id) it.copy(state = state, lastError = lastError) else it }
    }

    override suspend fun enqueue(record: OutboxRecord): OutboxRecord {
        records.value.firstOrNull { it.mutationId == record.mutationId }?.let { return it }
        val stored = record.copy(id = nextId++)
        records.value = records.value + stored
        return stored
    }

    override suspend fun dueRecords(ownerUid: String, nowMs: Long, limit: Int): List<OutboxRecord> =
        records.value.filter { it.ownerUid == ownerUid && it.state.isPending }.take(limit)

    override suspend fun apply(id: Long, decision: OutboxDecision) {
        setState(id, decision.state, decision.lastError)
    }

    override suspend fun remove(id: Long) {
        records.value = records.value.filterNot { it.id == id }
    }

    override fun observeCounts(ownerUid: String): Flow<OutboxCounts> =
        records.map { list ->
            val mine = list.filter { it.ownerUid == ownerUid }
            OutboxCounts(
                waiting = mine.count { it.state == OutboxState.WAITING },
                waitingPrecondition = mine.count { it.state == OutboxState.WAITING_PRECONDITION },
                conflicted = mine.count { it.state == OutboxState.CONFLICT },
                quarantined = mine.count { it.state == OutboxState.QUARANTINED },
            )
        }

    override fun observeEntity(ownerUid: String, entityRef: String): Flow<OutboxEntitySync> =
        records.map { list ->
            list.pick(ownerUid, entityRef)?.let {
                OutboxEntitySync.Queued(it.id, it.state, it.expectedVersion, it.serverVersion)
            } ?: OutboxEntitySync.None
        }

    override suspend fun findByEntityRef(ownerUid: String, entityRef: String): OutboxRecord? {
        if (yieldBeforeFind) yield()
        return records.value.pick(ownerUid, entityRef)
    }

    /** Та же выборка, что в запросе Room: конфликтная вперёд, за ней карантинная, иначе самая свежая. */
    private fun List<OutboxRecord>.pick(ownerUid: String, entityRef: String): OutboxRecord? =
        filter { it.ownerUid == ownerUid && it.entityRef == entityRef }
            .sortedWith(
                compareByDescending<OutboxRecord> { it.state == OutboxState.CONFLICT }
                    .thenByDescending { it.state == OutboxState.QUARANTINED }
                    .thenByDescending { it.createdAtMs }
                    .thenByDescending { it.id },
            ).firstOrNull()

    override suspend fun quarantined(ownerUid: String): List<OutboxRecord> =
        records.value.filter { it.ownerUid == ownerUid && it.state == OutboxState.QUARANTINED }

    override suspend fun countPending(ownerUid: String): Int =
        records.value.count { it.ownerUid == ownerUid && it.state.isPending }

    /** Вторая операция AD-8: после согласия на смену аккаунта записи прежнего владельца снимаются. */
    override suspend fun forget(ownerUid: String) {
        records.value = records.value.filterNot { it.ownerUid == ownerUid }
    }
}
