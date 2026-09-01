package com.tpov.schoolquiz.shared.core.outbox

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Очередь в памяти. Ведёт себя как настоящая ровно в том, что проверяют тесты движка. */
class FakeOutboxStore(
    private val policy: OutboxPolicy = OutboxPolicy(),
) : OutboxStore {

    private val records = MutableStateFlow<List<OutboxRecord>>(emptyList())
    private var nextId = 1L

    /** Удалённые записи — чтобы проверить, что отправленное именно удаляется, а не помечается. */
    val removed = mutableListOf<Long>()

    val all: List<OutboxRecord> get() = records.value

    fun seed(vararg record: OutboxRecord) {
        records.value = records.value + record
    }

    override suspend fun enqueue(record: OutboxRecord): OutboxRecord {
        val stored = record.copy(id = nextId++)
        records.value = records.value + stored
        return stored
    }

    override suspend fun dueRecords(
        ownerUid: String,
        nowMs: Long,
        limit: Int,
    ): List<OutboxRecord> =
        records.value
            .filter { it.ownerUid == ownerUid && policy.isDue(it, nowMs) }
            .sortedBy { it.createdAtMs }
            .take(limit)

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
    ): Flow<OutboxEntitySync?> = records.map { list -> list.pickForEntity(ownerUid, entityRef)?.toEntitySync() }

    override suspend fun findByEntityRef(
        ownerUid: String,
        entityRef: String,
    ): OutboxRecord? = records.value.pickForEntity(ownerUid, entityRef)

    /** Конфликтная вперёд всех, иначе самая свежая — то же правило, что в запросе Room. */
    private fun List<OutboxRecord>.pickForEntity(
        ownerUid: String,
        entityRef: String,
    ): OutboxRecord? =
        filter { it.ownerUid == ownerUid && it.entityRef == entityRef }
            .sortedWith(
                compareByDescending<OutboxRecord> { it.state == OutboxState.CONFLICT }
                    .thenByDescending { it.createdAtMs },
            ).firstOrNull()

    private fun OutboxRecord.toEntitySync() =
        OutboxEntitySync(
            recordId = id,
            state = state,
            expectedVersion = expectedVersion,
            serverVersion = serverVersion,
        )

    override suspend fun quarantined(ownerUid: String): List<OutboxRecord> =
        records.value.filter { it.ownerUid == ownerUid && it.state == OutboxState.QUARANTINED }

    override suspend fun countPending(ownerUid: String): Int =
        records.value.count { it.ownerUid == ownerUid && it.state.isPending }
}
