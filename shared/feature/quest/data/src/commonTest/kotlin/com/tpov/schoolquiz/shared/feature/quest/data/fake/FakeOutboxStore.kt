package com.tpov.schoolquiz.shared.feature.quest.data.fake

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

/**
 * Очередь в памяти для тестов фичи quest.
 *
 * Ведёт себя как настоящая ровно в том, что здесь проверяется: выбор записи по ссылке на сущность
 * — конфликтная вперёд всех, иначе самая свежая, — и удаление отправленного вместо надгробия (AD-4).
 */
class FakeOutboxStore : OutboxStore {

    private val records = MutableStateFlow<List<OutboxRecord>>(emptyList())
    private var nextId = 1L

    val all: List<OutboxRecord> get() = records.value

    /** Удалённые записи — чтобы проверить, что снятое намерение именно удаляется. */
    val removed = mutableListOf<Long>()

    /** Уступает поток перед выборкой по ссылке — чтобы тест мог столкнуть два постановщика. */
    var yieldBeforeFind = false

    /** Переводит запись в карантин — как это сделал бы движок по окончательному отказу. */
    fun quarantine(id: Long) {
        records.value =
            records.value.map { if (it.id == id) it.copy(state = OutboxState.QUARANTINED, lastError = "refused") else it }
    }

    override suspend fun enqueue(record: OutboxRecord): OutboxRecord {
        records.value.firstOrNull { it.mutationId == record.mutationId }?.let { return it }
        val stored = record.copy(id = nextId++)
        records.value = records.value + stored
        return stored
    }

    override suspend fun dueRecords(
        ownerUid: String,
        nowMs: Long,
        limit: Int,
    ): List<OutboxRecord> =
        records.value.filter { it.ownerUid == ownerUid && it.state.isPending }.take(limit)

    override suspend fun apply(
        id: Long,
        decision: OutboxDecision,
    ) {
        records.value =
            records.value.map {
                if (it.id != id) {
                    it
                } else {
                    it.copy(
                        state = decision.state,
                        nextRetryAtMs = decision.nextRetryAtMs,
                        attemptCount = decision.attemptCount,
                        lastError = decision.lastError,
                        serverVersion = decision.serverVersion,
                    )
                }
            }
    }

    override suspend fun remove(id: Long) {
        removed += id
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

    override fun observeEntity(
        ownerUid: String,
        entityRef: String,
    ): Flow<OutboxEntitySync> =
        records.map { list ->
            list.pickForEntity(ownerUid, entityRef)?.let {
                OutboxEntitySync.Queued(
                    recordId = it.id,
                    state = it.state,
                    expectedVersion = it.expectedVersion,
                    serverVersion = it.serverVersion,
                )
            } ?: OutboxEntitySync.None
        }

    override suspend fun findByEntityRef(
        ownerUid: String,
        entityRef: String,
    ): OutboxRecord? {
        if (yieldBeforeFind) yield()
        return records.value.pickForEntity(ownerUid, entityRef)
    }

    private fun List<OutboxRecord>.pickForEntity(
        ownerUid: String,
        entityRef: String,
    ): OutboxRecord? =
        filter { it.ownerUid == ownerUid && it.entityRef == entityRef }
            .sortedWith(
                compareByDescending<OutboxRecord> { it.state == OutboxState.CONFLICT }
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
