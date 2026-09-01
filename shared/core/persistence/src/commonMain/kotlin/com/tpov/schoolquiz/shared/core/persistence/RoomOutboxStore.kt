package com.tpov.schoolquiz.shared.core.persistence

import com.tpov.schoolquiz.shared.core.outbox.OutboxCounts
import com.tpov.schoolquiz.shared.core.outbox.OutboxDecision
import com.tpov.schoolquiz.shared.core.outbox.OutboxLimits
import com.tpov.schoolquiz.shared.core.outbox.OutboxRecord
import com.tpov.schoolquiz.shared.core.outbox.OutboxState
import com.tpov.schoolquiz.shared.core.outbox.OutboxStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Очередь на Room.
 *
 * Условие выборки живёт в запросе, а не в памяти: тащить всю таблицу, чтобы отфильтровать её в
 * Kotlin, значит поставить размер очереди в зависимость от памяти телефона. Предельный возраст
 * передаётся в запрос, потому что он общий для клиента и сервера и меняется вместе с ним (AD-22).
 */
class RoomOutboxStore(
    private val dao: OutboxDao,
    private val limits: OutboxLimits = OutboxLimits(),
) : OutboxStore {

    override suspend fun enqueue(record: OutboxRecord): OutboxRecord {
        val rowId = dao.insert(record.toEntity())
        if (rowId > 0L) return record.copy(id = rowId)
        // Такой ключ уже в очереди: повторная постановка того же намерения не создаёт вторую
        // запись (AD-2). Возвращаем ту, что уже лежит.
        return dao.findByMutationId(record.mutationId)?.toRecord() ?: record
    }

    override suspend fun dueRecords(
        ownerUid: String,
        nowMs: Long,
        limit: Int,
    ): List<OutboxRecord> = dao.due(ownerUid, nowMs, limits.maxAgeMs, limit).map { it.toRecord() }

    override suspend fun apply(
        id: Long,
        decision: OutboxDecision,
    ) = dao.applyDecision(
        id = id,
        state = decision.state.name,
        nextRetryAtMs = decision.nextRetryAtMs,
        attemptCount = decision.attemptCount,
        lastError = decision.lastError,
    )

    override suspend fun remove(id: Long) = dao.delete(id)

    override fun observeCounts(ownerUid: String): Flow<OutboxCounts> =
        dao.observeCounts(ownerUid).map { row ->
            OutboxCounts(
                waiting = row.waiting ?: 0,
                waitingPrecondition = row.waitingPrecondition ?: 0,
                conflicted = row.conflicted ?: 0,
                quarantined = row.quarantined ?: 0,
            )
        }

    override suspend fun quarantined(ownerUid: String): List<OutboxRecord> =
        dao.quarantined(ownerUid).map { it.toRecord() }

    override suspend fun countPending(ownerUid: String): Int = dao.countPending(ownerUid)

    /** Убирает всё, что осталось от прежнего владельца после смены аккаунта (AD-8). */
    suspend fun forget(ownerUid: String) = dao.deleteAllOf(ownerUid)
}

private fun OutboxRecord.toEntity() =
    OutboxEntity(
        id = if (id > 0L) id else 0L,
        mutationId = mutationId,
        ownerUid = ownerUid,
        operation = operation,
        payload = payload,
        entityRef = entityRef,
        expectedVersion = expectedVersion,
        state = state.name,
        attemptCount = attemptCount,
        nextRetryAtMs = nextRetryAtMs,
        lastError = lastError,
        createdAtMs = createdAtMs,
    )

private fun OutboxEntity.toRecord() =
    OutboxRecord(
        id = id,
        mutationId = mutationId,
        ownerUid = ownerUid,
        operation = operation,
        payload = payload,
        entityRef = entityRef,
        expectedVersion = expectedVersion,
        // Неизвестное имя состояния читается как карантин, а не как «в очередь»: строка из
        // будущей версии не должна снова уехать на сервер.
        state = runCatching { OutboxState.valueOf(state) }.getOrDefault(OutboxState.QUARANTINED),
        attemptCount = attemptCount,
        nextRetryAtMs = nextRetryAtMs,
        lastError = lastError,
        createdAtMs = createdAtMs,
    )
