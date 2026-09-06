package com.tpov.schoolquiz.shared.core.sync.fake

import com.tpov.schoolquiz.shared.core.outbox.OutboxCounts
import com.tpov.schoolquiz.shared.core.outbox.OutboxDecision
import com.tpov.schoolquiz.shared.core.outbox.OutboxEntitySync
import com.tpov.schoolquiz.shared.core.outbox.OutboxRecord
import com.tpov.schoolquiz.shared.core.outbox.OutboxStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Пустая очередь: тестам про след прогона она нужна только затем, чтобы снимок собрался.
 *
 * Счётчики к исходу прогона отношения не имеют — числа и состояния идут наружу раздельно (AD-14),
 * и подменять здесь очередь чем-то содержательным значило бы проверять не то.
 */
open class EmptyOutboxStore : OutboxStore {
    override suspend fun enqueue(record: OutboxRecord): OutboxRecord = record

    override suspend fun dueRecords(ownerUid: String, nowMs: Long, limit: Int): List<OutboxRecord> = emptyList()

    override suspend fun apply(id: Long, decision: OutboxDecision) = Unit

    override suspend fun remove(id: Long) = Unit

    override fun observeCounts(ownerUid: String): Flow<OutboxCounts> = flowOf(OutboxCounts())

    override fun observeEntity(ownerUid: String, entityRef: String): Flow<OutboxEntitySync> =
        flowOf(OutboxEntitySync.None)

    override suspend fun findByEntityRef(ownerUid: String, entityRef: String): OutboxRecord? = null

    override suspend fun quarantined(ownerUid: String): List<OutboxRecord> = emptyList()

    override suspend fun countPending(ownerUid: String): Int = 0

    override suspend fun forget(ownerUid: String) = Unit
}
