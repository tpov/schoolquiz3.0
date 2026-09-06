package com.tpov.schoolquiz.shared.core.persistence

import com.tpov.schoolquiz.shared.core.outbox.OutboxCounts
import com.tpov.schoolquiz.shared.core.outbox.OutboxDecision
import com.tpov.schoolquiz.shared.core.outbox.OutboxEntitySync
import com.tpov.schoolquiz.shared.core.outbox.OutboxLimits
import com.tpov.schoolquiz.shared.core.outbox.OutboxRecord
import com.tpov.schoolquiz.shared.core.outbox.OutboxRemainder
import com.tpov.schoolquiz.shared.core.outbox.OutboxRemainderSource
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
) : OutboxStore, OutboxRemainderSource {
    /**
     * Кладёт запись — отвергая ту, чья ссылка живёт не в том пространстве имён, что её операция
     * (NFR4).
     *
     * Отдельной проверки здесь нет намеренно: она стоит у [OutboxEntity], то есть у общей формы
     * строки, и срабатывает на `toOutboxEntity()` — до вставки, как и раньше. Своя копия правила в
     * хранилище держала бы его только для этого пути, а транзакции владеющих фич (AD-23) кладут
     * строку мимо хранилища, и для них правила бы не было.
     */
    override suspend fun enqueue(record: OutboxRecord): OutboxRecord {
        val rowId = dao.insert(record.toOutboxEntity())
        if (rowId > 0L) return record.copy(id = rowId)
        // Такой ключ уже в очереди: повторная постановка того же намерения не создаёт вторую
        // запись (AD-2). Возвращаем ту, что уже лежит.
        return dao.findByMutationId(record.mutationId)?.toRecord() ?: record
    }

    /**
     * Всё, что движок обязан рассмотреть в этом проходе, — а не только то, что уедет.
     *
     * Перезревшие идут первыми и вне зависимости от паузы: отправлять их нельзя (AD-1), но и
     * оставлять невидимыми нельзя. Пока выборка резала их по возрасту, запись, пролежавшая офлайн
     * дольше предельного срока, не попадала никуда: не отправлялась, в карантин не уходила, откат
     * не звала. Решение по возрасту принимает `OutboxPolicy` на стороне движка — здесь только
     * условие запроса, чтобы не тащить таблицу в память.
     */
    override suspend fun dueRecords(
        ownerUid: String,
        nowMs: Long,
        limit: Int,
    ): List<OutboxRecord> {
        val expired = dao.expired(ownerUid, nowMs, limits.maxAgeMs, limit)
        if (expired.size >= limit) return expired.map { it.toRecord() }
        val due = dao.due(ownerUid, nowMs, limits.maxAgeMs, limit - expired.size)
        return (expired + due).map { it.toRecord() }
    }

    override suspend fun apply(
        id: Long,
        decision: OutboxDecision,
    ) = dao.applyDecision(
        id = id,
        state = decision.state.name,
        nextRetryAtMs = decision.nextRetryAtMs,
        attemptCount = decision.attemptCount,
        lastError = decision.lastError,
        serverVersion = decision.serverVersion,
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

    /**
     * Что очередь знает про сущность. Пустой строки нет — есть ответ «неотправленного нет».
     *
     * `null` из запроса значит ровно одно: подходящей строки не нашлось. Наружу это едет явным
     * значением, потому что фича на нём строит пометку, а пометке нужно отличать «ничего не ждёт»
     * от «ещё не спросили».
     */
    override fun observeEntity(
        ownerUid: String,
        entityRef: String,
    ): Flow<OutboxEntitySync> =
        dao.observeByEntityRef(ownerUid, entityRef).map { row ->
            row?.toEntitySync() ?: OutboxEntitySync.None
        }

    override suspend fun findByEntityRef(
        ownerUid: String,
        entityRef: String,
    ): OutboxRecord? = dao.findByEntityRef(ownerUid, entityRef)?.toRecord()

    override suspend fun quarantined(ownerUid: String): List<OutboxRecord> =
        dao.quarantined(ownerUid).map { it.toRecord() }

    override suspend fun countPending(ownerUid: String): Int = dao.countPending(ownerUid)

    /**
     * Остаток владельца по всем пяти состояниям — тем же перечнем, что удаляет [forget].
     *
     * Считается запросом, а не выборкой в память: строк может быть сколько угодно, а нужно от них
     * одно число на состояние.
     */
    override suspend fun remainderOf(ownerUid: String): OutboxRemainder =
        dao.countRemainder(ownerUid).let { row ->
            OutboxRemainder(
                waiting = row.waiting ?: 0,
                waitingPrecondition = row.waitingPrecondition ?: 0,
                conflicted = row.conflicted ?: 0,
                quarantined = row.quarantined ?: 0,
                synchronous = row.synchronous ?: 0,
            )
        }

    override suspend fun ownersWithRecords(): List<String> = dao.ownersWithRecords()

    /** Убирает всё, что осталось от прежнего владельца после смены аккаунта (AD-8). */
    override suspend fun forget(ownerUid: String) = dao.deleteAllOf(ownerUid)
}

/**
 * Запись очереди как строка таблицы.
 *
 * Открыта наружу намеренно: владеющая фича, снимающая конфликт своей транзакцией вместе со своей
 * половиной изменения (AD-23), обязана класть в очередь ровно ту же форму строки, что и ядро.
 */
fun OutboxRecord.toOutboxEntity() =
    OutboxEntity(
        id = if (id > 0L) id else 0L,
        mutationId = mutationId,
        ownerUid = ownerUid,
        operation = operation,
        payload = payload,
        entityRef = entityRef,
        expectedVersion = expectedVersion,
        serverVersion = serverVersion,
        state = state.name,
        attemptCount = attemptCount,
        nextRetryAtMs = nextRetryAtMs,
        lastError = lastError,
        createdAtMs = createdAtMs,
    )

/**
 * Строка пометки в состояние сущности.
 *
 * Неизвестное имя состояния читается как карантин — тем же правилом, что и в [toRecord]: строка
 * из будущей версии не уедет, и показывать её как ожидание значило бы обещать игроку отправку,
 * которой не будет.
 */
private fun OutboxEntitySyncRow.toEntitySync() =
    OutboxEntitySync.Queued(
        recordId = recordId,
        state = runCatching { OutboxState.valueOf(state) }.getOrDefault(OutboxState.QUARANTINED),
        expectedVersion = expectedVersion,
        serverVersion = serverVersion,
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
        serverVersion = serverVersion,
        // Неизвестное имя состояния читается как карантин, а не как «в очередь»: строка из
        // будущей версии не должна снова уехать на сервер.
        state = runCatching { OutboxState.valueOf(state) }.getOrDefault(OutboxState.QUARANTINED),
        attemptCount = attemptCount,
        nextRetryAtMs = nextRetryAtMs,
        lastError = lastError,
        createdAtMs = createdAtMs,
    )
