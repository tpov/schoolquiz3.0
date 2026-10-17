package com.tpov.schoolquiz.android.feature.tournament.presentation.fake

import com.tpov.schoolquiz.shared.core.outbox.OutboxCounts
import com.tpov.schoolquiz.shared.core.outbox.OutboxDecision
import com.tpov.schoolquiz.shared.core.outbox.OutboxEntitySync
import com.tpov.schoolquiz.shared.core.outbox.OutboxRecord
import com.tpov.schoolquiz.shared.core.outbox.OutboxState
import com.tpov.schoolquiz.shared.core.outbox.OutboxStore
import com.tpov.schoolquiz.shared.core.outbox.MutationReceipt
import com.tpov.schoolquiz.shared.core.outbox.MutationReceiptStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/** Минимальный фейк хранилища очереди: строки лежат, наблюдение сущности живое. */
class FakeOutboxStore : OutboxStore {
    val rows = mutableListOf<OutboxRecord>()
    private val entitySync = MutableStateFlow<OutboxEntitySync>(OutboxEntitySync.None)

    override suspend fun enqueue(record: OutboxRecord): OutboxRecord {
        val stored = record.copy(id = rows.size + 1L)
        rows += stored
        entitySync.value = OutboxEntitySync.Queued(stored.id, OutboxState.WAITING)
        return stored
    }

    override suspend fun dueRecords(
        ownerUid: String,
        nowMs: Long,
        limit: Int,
    ): List<OutboxRecord> = rows

    override suspend fun apply(
        id: Long,
        decision: OutboxDecision,
    ) = Unit

    override suspend fun remove(id: Long) {
        rows.removeAll { it.id == id }
        entitySync.value = OutboxEntitySync.None
    }

    override fun observeCounts(ownerUid: String): Flow<OutboxCounts> =
        entitySync.asStateFlow().map { OutboxCounts(waiting = if (it is OutboxEntitySync.Queued) 1 else 0) }

    override fun observeEntity(
        ownerUid: String,
        entityRef: String,
    ): Flow<OutboxEntitySync> = entitySync.asStateFlow()

    override suspend fun findByEntityRef(
        ownerUid: String,
        entityRef: String,
    ): OutboxRecord? = rows.lastOrNull { it.ownerUid == ownerUid && it.entityRef == entityRef }

    override suspend fun quarantined(ownerUid: String): List<OutboxRecord> = emptyList()

    override suspend fun countPending(ownerUid: String): Int = rows.size

    override suspend fun forget(ownerUid: String) {
        rows.clear()
    }

    /** Движок забрал доставленную строку: сущность снова «неотправленного нет» (AD-4). */
    fun markDelivered() {
        rows.clear()
        entitySync.value = OutboxEntitySync.None
    }
}

/** Хранилище квитанций с настраиваемым ответом: тест решает, что «сервер уже ответил». */
class FakeReceiptStore : MutationReceiptStore {
    val persisted = mutableListOf<MutationReceipt>()
    private val sync = MutableStateFlow<List<MutationReceipt>>(emptyList())

    /** Ключ мутации -> квитанция: тест кладёт ответ приёмника до/во время ожидания компонента. */
    val receiptsByKey = mutableMapOf<String, MutationReceipt>()

    override suspend fun persist(receipt: MutationReceipt) {
        persisted += receipt
        receiptsByKey[receipt.mutationId] = receipt
        sync.value = receiptsByKey.values.toList()
    }

    override suspend fun find(
        ownerUid: String,
        mutationId: String,
    ): MutationReceipt? = receiptsByKey[mutationId]

    override fun observeEntity(
        ownerUid: String,
        entityRef: String,
    ): Flow<List<MutationReceipt>> =
        sync.asStateFlow().map { rows -> rows.filter { it.entityRef == entityRef } }
}
