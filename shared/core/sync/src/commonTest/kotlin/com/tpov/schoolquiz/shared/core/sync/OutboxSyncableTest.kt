package com.tpov.schoolquiz.shared.core.sync

import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.network.SyncFailure
import com.tpov.schoolquiz.shared.core.outbox.MutationTransport
import com.tpov.schoolquiz.shared.core.outbox.NoLocalEffect
import com.tpov.schoolquiz.shared.core.outbox.OutboxCounts
import com.tpov.schoolquiz.shared.core.outbox.OutboxDecision
import com.tpov.schoolquiz.shared.core.outbox.OutboxEngine
import com.tpov.schoolquiz.shared.core.outbox.OutboxRecord
import com.tpov.schoolquiz.shared.core.outbox.OutboxState
import com.tpov.schoolquiz.shared.core.outbox.OutboxStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

/**
 * Очередь как участник обычной синхронизации — и единственный источник того, что игрок вообще
 * узнаёт про её ход. До сих пор наружу не выходило ничего, кроме `Log.w`.
 */
class OutboxSyncableTest {

    private val now = 1_700_000_000_000L

    private class Store(private val records: MutableList<OutboxRecord>) : OutboxStore {
        override suspend fun enqueue(record: OutboxRecord): OutboxRecord = record

        override suspend fun dueRecords(ownerUid: String, nowMs: Long, limit: Int): List<OutboxRecord> =
            records.filter { it.ownerUid == ownerUid }

        override suspend fun apply(id: Long, decision: OutboxDecision) = Unit

        override suspend fun remove(id: Long) {
            records.removeAll { it.id == id }
        }

        override fun observeCounts(ownerUid: String): Flow<OutboxCounts> = flowOf(OutboxCounts())

        override suspend fun quarantined(ownerUid: String): List<OutboxRecord> = emptyList()

        override suspend fun countPending(ownerUid: String): Int = records.size
    }

    private fun record() =
        OutboxRecord(
            id = 1L,
            mutationId = "m-1",
            ownerUid = "uid-1",
            operation = "OP",
            payload = "{}",
            state = OutboxState.WAITING,
            createdAtMs = now,
        )

    private fun syncable(
        uid: String?,
        transport: MutationTransport,
        status: SyncStatusRepository,
        store: OutboxStore = Store(mutableListOf(record())),
    ) = OutboxSyncable(
        engine = OutboxEngine(store, transport, clock = { now }, onQuarantined = NoLocalEffect()),
        currentUidProvider = { uid },
        status = status,
        clock = { now },
    )

    private fun status(store: OutboxStore = Store(mutableListOf())) =
        InMemorySyncStatusRepository(store, flowOf("uid-1"))

    @Test
    fun `given the run goes through then the success time is recorded`() = runTest {
        val status = status()

        val result = syncable("uid-1", { Result.success(Unit) }, status).sync()

        assertTrue(result.isSuccess)
        assertEquals(now, status.observeStatus().first().lastSuccessAtMs)
    }

    @Test
    fun `given the transport reports no network then the type reaches the status, not a string`() = runTest {
        // Решать по тексту сообщения нельзя, и показывать его игроку — тоже (AD-15).
        val status = status()

        syncable("uid-1", { Result.failure(SyncFailure(SyncError.NoNetwork)) }, status).sync()

        // Записи ушли в ожидание, сам проход завершился — неудачей это не считается.
        assertEquals(now, status.observeStatus().first().lastSuccessAtMs)
    }

    @Test
    fun `given no account then nothing is drained and nothing is recorded`() = runTest {
        val status = status()
        var attempts = 0

        val result = syncable(null, { attempts++; Result.success(Unit) }, status).sync()

        assertTrue(result.isSuccess)
        assertEquals(0, attempts)
        assertEquals(0L, status.observeStatus().first().lastSuccessAtMs, "прохода не было — и отметки нет")
    }

    @Test
    fun `given a blank uid then it is treated as no account`() = runTest {
        var attempts = 0

        syncable("   ", { attempts++; Result.success(Unit) }, status()).sync()

        assertEquals(0, attempts)
    }

    @Test
    fun `given the store itself throws then the failure is reported and typed`() = runTest {
        val broken =
            object : OutboxStore by Store(mutableListOf()) {
                override suspend fun dueRecords(ownerUid: String, nowMs: Long, limit: Int): List<OutboxRecord> =
                    throw IllegalStateException("database is gone")
            }
        val status = status()

        val result = syncable("uid-1", { Result.success(Unit) }, status, broken).sync()

        assertTrue(result.isFailure)
        assertTrue(status.observeStatus().first().lastError is SyncError.Unknown)
    }
}
