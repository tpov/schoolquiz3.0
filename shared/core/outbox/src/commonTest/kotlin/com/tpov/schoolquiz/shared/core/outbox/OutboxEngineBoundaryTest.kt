package com.tpov.schoolquiz.shared.core.outbox

import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.network.SyncFailure
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OutboxEngineBoundaryTest {
    private val uid = "owner"
    private val now = 30L * 24 * 60 * 60 * 1000
    private var session = OutboxSession(uid, 1)

    private fun record(
        id: Long,
        createdAtMs: Long = now,
    ) = OutboxRecord(id, "mutation-$id", uid, "OP", "{}", OutboxState.WAITING, createdAtMs)

    private fun engine(
        store: OutboxStore,
        receipts: MutationReceiptStore = FakeMutationReceiptStore(),
        onQuarantined: QuarantineListener = QuarantineListener { error("Unexpected quarantine") },
        transport: MutationTransport = MutationTransport { Result.success(MutationResponse("{}")) },
    ) = OutboxEngine(
        store,
        transport,
        clock = { now },
        onQuarantined = onQuarantined,
        receipts = receipts,
        sessions = { session },
    )

    @Test
    fun `settlement already recorded acks the row with the marker receipt`() =
        runTest {
            val row = OutboxRecord(
                id = 7L,
                mutationId = "lesson_runner-SUBMIT_ATTEMPT-2e4b91c8",
                ownerUid = uid,
                operation = "lesson_runner.SUBMIT_ATTEMPT",
                payload = "{}",
                state = OutboxState.WAITING,
                createdAtMs = now,
            )
            val store = FakeOutboxStore().also { it.seed(row) }
            val receipts = FakeMutationReceiptStore()
            val summary =
                engine(
                    store,
                    receipts,
                    transport = {
                        Result.failure(
                            SyncFailure(SyncError.SettlementAlreadyRecorded()),
                        )
                    },
                ).drain(uid)

            assertEquals(1, summary.sent, "реплей settle — не отказ: эффект уже на сервере")
            assertEquals(0, summary.quarantined)
            assertTrue(store.all.isEmpty(), "строка ACK-ается удалением, не крутится")
            val receipt = receipts.find(uid, row.mutationId)
            assertEquals(
                SettlementReplayReceipt.PAYLOAD_JSON,
                receipt?.payloadJson,
                "маркер объясняет, почему квитанции сервера нет",
            )
        }

    @Test
    fun `settlement already recorded on a foreign operation parks the row in conflict`() =
        runTest {
            // Гард ветви ACK — по операции: маркер принадлежит попытке урока. Чужая операция с той
            // же ошибкой едет в политику, где disposition CONFLICT паркует её: серверный отказ
            // терминален, повторять его нельзя, но и ACK с чужим маркером выдумывать нельзя.
            val row = record(11).copy(operation = "private_quiz.SUBMIT_ATTEMPT")
            val store = FakeOutboxStore().also { it.seed(row) }
            val receipts = FakeMutationReceiptStore()
            val summary =
                engine(
                    store,
                    receipts,
                    transport = {
                        Result.failure(
                            SyncFailure(SyncError.SettlementAlreadyRecorded()),
                        )
                    },
                ).drain(uid)

            assertEquals(0, summary.sent, "чужая операция не ACK-ается маркером попытки урока")
            assertEquals(1, summary.conflicted, "политика паркует запись, а не повторяет отказ")
            assertEquals(0, summary.quarantined)
            assertEquals(1, store.all.size)
            assertEquals(OutboxState.CONFLICT, store.all.single().state)
            assertTrue(store.removed.isEmpty())
            assertNull(receipts.find(uid, row.mutationId), "квитанцию выдумывать нельзя")
        }

    @Test
    fun `receipt lookup failure does not expire or quarantine an old pending row`() =
        runTest {
            val row = record(1, createdAtMs = 0).copy(attemptCount = 9)
            val store = FakeOutboxStore().also { it.seed(row) }
            val receipts =
                object : FakeMutationReceiptStore() {
                    override suspend fun find(
                        ownerUid: String,
                        mutationId: String,
                    ): MutationReceipt? = error("disk unavailable")
                }
            val summary =
                engine(
                    store,
                    receipts,
                    transport = { error("Receipt lookup failed before transport") },
                ).drain(uid)
            assertEquals(1, summary.retried)
            assertEquals(0, summary.quarantined)
            assertIs<SyncError.Unknown>(summary.lastError)
            assertEquals(listOf(row), store.all)
        }

    @Test
    fun `receipt lookup cancellation propagates without queue changes`() =
        runTest {
            val row = record(1)
            val store = FakeOutboxStore().also { it.seed(row) }
            val receipts =
                object : FakeMutationReceiptStore() {
                    override suspend fun find(
                        ownerUid: String,
                        mutationId: String,
                    ): MutationReceipt? = throw CancellationException("cancelled read")
                }
            assertFailsWith<CancellationException> { engine(store, receipts).drain(uid) }
            assertEquals(listOf(row), store.all)
            assertTrue(store.removed.isEmpty())
        }

    @Test
    fun `generation change during receipt persistence preserves proof but defers ack`() =
        runTest {
            val row = record(1)
            val store = FakeOutboxStore().also { it.seed(row) }
            val receipts =
                object : FakeMutationReceiptStore() {
                    override suspend fun persist(receipt: MutationReceipt) {
                        super.persist(receipt)
                        session = session.copy(generation = 2)
                    }
                }
            val summary = engine(store, receipts).drain(uid)
            assertEquals(1, summary.retried)
            assertEquals(0, summary.sent)
            assertEquals(listOf(row), store.all)
            assertNotNull(receipts.find(uid, row.mutationId))
            val resumed = engine(store, receipts, transport = { error("Cached proof must bypass network") }).drain(uid)
            assertEquals(1, resumed.sent)
            assertTrue(store.all.isEmpty())
        }

    @Test
    fun `successful quarantine callback changing A to B cannot apply old state`() =
        runTest {
            assertQuarantineGenerationChange("other", fails = false)
        }

    @Test
    fun `successful quarantine callback returning A through B cannot apply old state`() =
        runTest {
            assertQuarantineGenerationChange(uid, fails = false)
        }

    @Test
    fun `failed quarantine callback changing A to B cannot apply deferred retry`() =
        runTest {
            assertQuarantineGenerationChange("other", fails = true)
        }

    @Test
    fun `failed quarantine callback returning A through B cannot apply deferred retry`() =
        runTest {
            assertQuarantineGenerationChange(uid, fails = true)
        }

    private suspend fun assertQuarantineGenerationChange(
        finalUid: String,
        fails: Boolean,
    ) {
        val rows = listOf(record(1), record(2))
        var applies = 0
        val store =
            object : FakeOutboxStore() {
                override suspend fun apply(
                    id: Long,
                    decision: OutboxDecision,
                ) {
                    applies++
                    super.apply(id, decision)
                }
            }.also { it.seed(*rows.toTypedArray()) }
        var callbacks = 0
        var sends = 0
        val summary =
            engine(
                store,
                onQuarantined = {
                    callbacks++
                    session = OutboxSession("other", 2)
                    session = OutboxSession(finalUid, 3)
                    yield()
                    if (fails) error("callback failed after account switch")
                },
                transport = {
                    sends++
                    Result.failure(SyncFailure(SyncError.Refused("rejected")))
                },
            ).drain(uid)
        assertEquals(1, sends)
        assertEquals(1, callbacks)
        assertEquals(0, applies)
        assertEquals(1, summary.examined)
        assertEquals(1, summary.retried)
        assertEquals(0, summary.quarantined)
        assertEquals(null, summary.lastError)
        assertEquals(rows, store.all)
        assertTrue(store.removed.isEmpty())
    }

    @Test
    fun `cancellation from quarantine callback propagates without state write`() =
        runTest {
            val row = record(1)
            val store = FakeOutboxStore().also { it.seed(row) }
            val worker =
                engine(
                    store,
                    onQuarantined = { throw CancellationException("cancelled callback") },
                    transport = { Result.failure(SyncFailure(SyncError.Refused("rejected"))) },
                )
            assertFailsWith<CancellationException> { worker.drain(uid) }
            assertEquals(listOf(row), store.all)
        }

    @Test
    fun `default hundred page bound widens prefixes without revisiting unchanged due rows`() =
        runTest {
            val count = 5001L
            val limits = mutableListOf<Int>()
            val store =
                object : FakeOutboxStore() {
                    override suspend fun dueRecords(
                        ownerUid: String,
                        nowMs: Long,
                        limit: Int,
                    ): List<OutboxRecord> {
                        limits += limit
                        return super.dueRecords(ownerUid, nowMs, limit)
                    }
                }.also { it.seed(*(1L..count).map { record(it) }.toTypedArray()) }
            val visits = mutableListOf<String>()
            val receipts =
                object : FakeMutationReceiptStore() {
                    override suspend fun find(
                        ownerUid: String,
                        mutationId: String,
                    ): MutationReceipt? {
                        visits += mutationId
                        error("unchanged due row")
                    }
                }
            val summary = engine(store, receipts, transport = { error("Receipt read failed") }).drain(uid)
            assertEquals(5000, summary.examined)
            assertEquals(5000, summary.retried)
            assertEquals(100, limits.size)
            assertEquals((1..100).map { it * 50 }, limits)
            assertEquals(5000, visits.size)
            assertEquals(5000, visits.toSet().size)
            assertEquals(count.toInt(), store.all.size)
            assertTrue(store.removed.isEmpty())
        }

    @Test
    fun `page bounds remain validated by unchanged public constructor`() {
        for ((batch, pages) in listOf(0 to 1, 51 to 1, 1 to 0, 1 to 101)) {
            assertFailsWith<IllegalArgumentException> {
                OutboxEngine(
                    FakeOutboxStore(),
                    transport = { error("Not constructed") },
                    clock = { now },
                    onQuarantined = { error("Not constructed") },
                    receipts = FakeMutationReceiptStore(),
                    sessions = { session },
                    batchSize = batch,
                    maxBatchesPerRun = pages,
                )
            }
        }
    }
}
