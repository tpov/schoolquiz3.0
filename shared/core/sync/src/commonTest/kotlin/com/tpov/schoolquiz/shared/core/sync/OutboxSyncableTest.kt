package com.tpov.schoolquiz.shared.core.sync

import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.network.SyncFailure
import com.tpov.schoolquiz.shared.core.network.syncErrorOrNull
import com.tpov.schoolquiz.shared.core.outbox.MutationResponse
import com.tpov.schoolquiz.shared.core.outbox.MutationTransport
import com.tpov.schoolquiz.shared.core.outbox.NoLocalEffect
import com.tpov.schoolquiz.shared.core.outbox.OutboxCounts
import com.tpov.schoolquiz.shared.core.outbox.OutboxDecision
import com.tpov.schoolquiz.shared.core.outbox.OutboxEngine
import com.tpov.schoolquiz.shared.core.outbox.OutboxEntitySync
import com.tpov.schoolquiz.shared.core.outbox.OutboxRecord
import com.tpov.schoolquiz.shared.core.outbox.OutboxSession
import com.tpov.schoolquiz.shared.core.outbox.OutboxState
import com.tpov.schoolquiz.shared.core.outbox.OutboxStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Очередь как участник обычной синхронизации.
 *
 * Свой след она не пишет: успех — свойство всего прогона, и записывает его [SyncRun]. Здесь
 * проверяется ровно то, что шаг обязан рассказать прогону: довёз он или нет и по какой ветви.
 */
class OutboxSyncableTest {
    private val now = 1_700_000_000_000L

    private class Store(private val records: MutableList<OutboxRecord>) : OutboxStore {
        override suspend fun enqueue(record: OutboxRecord): OutboxRecord = record

        override suspend fun dueRecords(
            ownerUid: String,
            nowMs: Long,
            limit: Int,
        ): List<OutboxRecord> = records.filter { it.ownerUid == ownerUid }

        override suspend fun apply(
            id: Long,
            decision: OutboxDecision,
        ) = Unit

        override suspend fun remove(id: Long) {
            records.removeAll { it.id == id }
        }

        override fun observeCounts(ownerUid: String): Flow<OutboxCounts> = flowOf(OutboxCounts())

        override fun observeEntity(
            ownerUid: String,
            entityRef: String,
        ): Flow<OutboxEntitySync> = flowOf(OutboxEntitySync.None)

        override suspend fun findByEntityRef(
            ownerUid: String,
            entityRef: String,
        ): OutboxRecord? = null

        override suspend fun quarantined(ownerUid: String): List<OutboxRecord> = emptyList()

        override suspend fun countPending(ownerUid: String): Int = records.size

        /** Вторая операция AD-8: после согласия на смену аккаунта записи прежнего владельца снимаются. */
        override suspend fun forget(ownerUid: String) {
            records.removeAll { it.ownerUid == ownerUid }
        }
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

    private fun noNetwork() = syncable("uid-1", MutationTransport { Result.failure(SyncFailure(SyncError.NoNetwork)) })

    private fun syncable(
        uid: String?,
        transport: MutationTransport,
        store: OutboxStore = Store(mutableListOf(record())),
    ) = OutboxSyncable(
        engine =
            OutboxEngine(
                store,
                transport,
                clock = { now },
                onQuarantined = NoLocalEffect(),
                receipts = FakeMutationReceiptStore(),
                sessions = { uid?.let { OutboxSession(it, 0) } },
            ),
        currentUidProvider = { uid },
    )

    @Test
    fun `given the run goes through then the step reports success`() =
        runTest {
            val result = syncable("uid-1", MutationTransport { Result.success(MutationResponse("{}")) }).sync()

            assertTrue(result.isSuccess)
        }

    @Test
    fun `given nothing could be delivered then the step reports failure, not success`() =
        runTest {
            // Прежде здесь стояло обратное утверждение — и это был дефект: drain по контракту не
            // бросает, поэтому «вернулся» означало «успех», и при любом числе неудач прогон считался
            // прошедшим. Успех — это когда ждать больше нечего.
            val result = noNetwork().sync()

            assertTrue(result.isFailure, "проход, который ничего не довёз, успехом не считается")
        }

    @Test
    fun `given the pass could not deliver then the branch travels up, not the text`() =
        runTest {
            // Ровно то, что игрок увидит строкой причины: ветвь, а не `throwable.message` (AD-15).
            val result = noNetwork().sync()

            assertEquals(SyncError.NoNetwork, result.syncErrorOrNull())
        }

    @Test
    fun `given a refusal then the branch is refusal, and it is not the network branch`() =
        runTest {
            val refused = MutationTransport { Result.failure(SyncFailure(SyncError.Refused("нет золота"))) }

            val result = syncable("uid-1", refused).sync()

            assertEquals(SyncError.Refused("нет золота"), result.syncErrorOrNull())
        }

    @Test
    fun `given no account then nothing is drained and the step does not fail the run`() =
        runTest {
            var attempts = 0

            val result =
                syncable(
                    null,
                    MutationTransport {
                        attempts++
                        Result.success(MutationResponse("{}"))
                    },
                ).sync()

            assertTrue(result.isSuccess)
            assertEquals(0, attempts)
        }

    @Test
    fun `given a blank uid then it is treated as no account`() =
        runTest {
            var attempts = 0

            syncable(
                "   ",
                MutationTransport {
                    attempts++
                    Result.success(MutationResponse("{}"))
                },
            ).sync()

            assertEquals(0, attempts)
        }

    @Test
    fun `given the store itself throws then the failure travels up as is`() =
        runTest {
            val broken =
                object : OutboxStore by Store(mutableListOf()) {
                    override suspend fun dueRecords(
                        ownerUid: String,
                        nowMs: Long,
                        limit: Int,
                    ): List<OutboxRecord> = throw IllegalStateException("database is gone")
                }

            val result = syncable("uid-1", { Result.success(MutationResponse("{}")) }, broken).sync()

            assertTrue(result.isFailure)
            // Ветви у отказа хранилища нет — её назовёт прогон, и это будет «неизвестно», а не текст.
            assertNull(result.syncErrorOrNull())
        }
}
