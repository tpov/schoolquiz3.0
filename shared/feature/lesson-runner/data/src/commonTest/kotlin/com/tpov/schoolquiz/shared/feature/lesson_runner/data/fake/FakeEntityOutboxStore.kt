package com.tpov.schoolquiz.shared.feature.lesson_runner.data.fake

import com.tpov.schoolquiz.shared.core.outbox.OutboxCounts
import com.tpov.schoolquiz.shared.core.outbox.OutboxDecision
import com.tpov.schoolquiz.shared.core.outbox.OutboxEntitySync
import com.tpov.schoolquiz.shared.core.outbox.OutboxRecord
import com.tpov.schoolquiz.shared.core.outbox.OutboxState
import com.tpov.schoolquiz.shared.core.outbox.OutboxStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Очередь в памяти ровно в той части, которой пользуется пометка сущности.
 *
 * Канон — `shared/core/outbox/src/commonTest/.../FakeOutboxStore.kt`; он в этот модуль не
 * дотягивается, а весь его объём здесь не нужен. Остальные операции намеренно падают: тест,
 * который их позовёт, соврал бы про предмет проверки.
 */
class FakeEntityOutboxStore : OutboxStore {

    private val records = MutableStateFlow<List<OutboxRecord>>(emptyList())
    private var nextId = 1L

    /** Кладёт запись так же, как её положил бы писатель фичи. */
    fun seed(
        ownerUid: String,
        entityRef: String,
        state: OutboxState,
        createdAtMs: Long = 0L,
    ): OutboxRecord {
        val record =
            OutboxRecord(
                id = nextId++,
                mutationId = "mutation-${nextId}",
                ownerUid = ownerUid,
                operation = "lesson_runner.SUBMIT_ATTEMPT",
                payload = "{}",
                state = state,
                createdAtMs = createdAtMs,
                entityRef = entityRef,
            )
        records.value = records.value + record
        return record
    }

    /** Движок перевёл запись в другое состояние — подписчик обязан узнать без пересоздания. */
    fun moveTo(
        id: Long,
        state: OutboxState,
    ) {
        records.value = records.value.map { if (it.id == id) it.copy(state = state) else it }
    }

    override fun observeEntity(
        ownerUid: String,
        entityRef: String,
    ): Flow<OutboxEntitySync> = records.map { list -> list.pick(ownerUid, entityRef) }

    override suspend fun findByEntityRef(
        ownerUid: String,
        entityRef: String,
    ): OutboxRecord? =
        records.value.firstOrNull { it.ownerUid == ownerUid && it.entityRef == entityRef }

    private fun List<OutboxRecord>.pick(
        ownerUid: String,
        entityRef: String,
    ): OutboxEntitySync =
        filter { it.ownerUid == ownerUid && it.entityRef == entityRef }
            .sortedWith(
                compareByDescending<OutboxRecord> { it.state == OutboxState.CONFLICT }
                    .thenByDescending { it.state == OutboxState.QUARANTINED }
                    .thenByDescending { it.createdAtMs },
            ).firstOrNull()
            ?.let { OutboxEntitySync.Queued(recordId = it.id, state = it.state) }
            ?: OutboxEntitySync.None

    override suspend fun enqueue(record: OutboxRecord): OutboxRecord = unused()

    override suspend fun dueRecords(
        ownerUid: String,
        nowMs: Long,
        limit: Int,
    ): List<OutboxRecord> = unused()

    override suspend fun apply(
        id: Long,
        decision: OutboxDecision,
    ): Unit = unused()

    override suspend fun remove(id: Long): Unit = unused()

    override fun observeCounts(ownerUid: String): Flow<OutboxCounts> = unused()

    override suspend fun quarantined(ownerUid: String): List<OutboxRecord> = unused()

    override suspend fun countPending(ownerUid: String): Int = unused()

    override suspend fun forget(ownerUid: String): Unit = unused()

    private fun unused(): Nothing =
        error("FakeEntityOutboxStore отвечает только на вопросы про одну сущность")
}
