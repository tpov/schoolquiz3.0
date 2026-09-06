package com.tpov.schoolquiz.shared.core.outbox

import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.network.SyncFailure
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OutboxReceiptDrainTest {
    private val uid = "owner"
    private val now = 30L * 24 * 60 * 60 * 1000
    private var session = OutboxSession(uid, 1)

    private fun record(
        id: Long,
        operation: String = "OP",
        createdAtMs: Long = now,
    ) = OutboxRecord(
        id,
        "mutation-$id",
        uid,
        operation,
        "{}",
        OutboxState.WAITING,
        createdAtMs,
        entityRef = "lesson_runner:attempt-$id",
    )

    private fun receipt(record: OutboxRecord) =
        MutationReceipt(
            record.ownerUid,
            record.mutationId,
            record.operation,
            record.entityRef,
            "{\"accepted\":true}",
            now,
        )

    private fun engine(
        store: OutboxStore,
        receipts: MutationReceiptStore = FakeMutationReceiptStore(),
        maxBatches: Int = 100,
        onQuarantined: QuarantineListener = NoLocalEffect(),
        transport: MutationTransport = MutationTransport { Result.success(MutationResponse("{\"accepted\":true}")) },
    ) = OutboxEngine(
        store,
        transport,
        clock = { now },
        onQuarantined = onQuarantined,
        receipts = receipts,
        sessions = { session },
        maxBatchesPerRun = maxBatches,
    )

    @Test
    fun `one run drains more than two full batches`() =
        runTest {
            val store = FakeOutboxStore()
            store.seed(*(1L..127L).map { record(it) }.toTypedArray())
            val summary = engine(store).drain(uid)
            assertEquals(127, summary.examined)
            assertEquals(127, summary.sent)
            assertTrue(store.all.isEmpty())
        }

    @Test
    fun `unchanged due receipt failures cannot hide the tail or spin`() =
        runTest {
            val store = FakeOutboxStore()
            store.seed(*(1L..120L).map { record(it) }.toTypedArray())
            val seen = mutableListOf<Long>()
            val receipts =
                object : FakeMutationReceiptStore() {
                    override suspend fun persist(receipt: MutationReceipt) {
                        if (receipt.mutationId.removePrefix("mutation-").toLong() <= 50) error("disk full")
                        super.persist(receipt)
                    }
                }
            val summary =
                engine(store, receipts, transport = { row ->
                    seen += row.id
                    Result.success(MutationResponse("{}"))
                }).drain(uid)
            assertEquals(120, summary.examined)
            assertEquals(120, seen.distinct().size)
            assertEquals(120, seen.size)
            assertEquals(50, summary.retried)
            assertEquals(70, summary.sent)
            assertEquals(0, summary.quarantined)
            assertTrue(store.all.all { it.state == OutboxState.WAITING && it.attemptCount == 0 })
        }

    @Test
    fun `receipt survives queue removal and is stored before ack`() =
        runTest {
            val receipts = FakeMutationReceiptStore()
            val row = record(1)
            val store =
                object : FakeOutboxStore() {
                    override suspend fun remove(id: Long) {
                        assertNotNull(receipts.find(uid, row.mutationId))
                        super.remove(id)
                    }
                }
            store.seed(row)
            engine(store, receipts).drain(uid)
            assertTrue(store.all.isEmpty())
            assertEquals(receipt(row), receipts.find(uid, row.mutationId))
        }

    @Test
    fun `restart after failed ack uses saved response without repeating network or TTL rollback`() =
        runTest {
            val receipts = FakeMutationReceiptStore()
            val row = record(1, createdAtMs = 0)
            receipts.persist(receipt(row))
            val store =
                object : FakeOutboxStore() {
                    var failRemoval = true

                    override suspend fun remove(id: Long) {
                        if (failRemoval) error("storage unavailable")
                        super.remove(id)
                    }
                }
            store.seed(row)
            val neverSend = MutationTransport { error("cached receipt must prevent network replay") }
            assertEquals(1, engine(store, receipts, transport = neverSend).drain(uid).retried)
            store.failRemoval = false
            assertEquals(1, engine(store, receipts, transport = neverSend).drain(uid).sent)
            assertEquals(receipt(row), receipts.find(uid, row.mutationId))
        }

    @Test
    fun `old attempt and exhausted retries remain replayable through permanent receipt`() =
        runTest {
            val store = FakeOutboxStore()
            store.seed(record(1, OutboxOperations.SUBMIT_ATTEMPT, 0).copy(attemptCount = 9))
            val failed = engine(store, transport = { Result.failure(SyncFailure(SyncError.NoNetwork)) }).drain(uid)
            assertEquals(1, failed.retried)
            assertEquals(0, failed.quarantined)
            assertEquals(OutboxState.WAITING, store.all.single().state)
            val old = store.all.single().copy(nextRetryAtMs = 0)
            val replay = FakeOutboxStore().also { it.seed(old) }
            assertEquals(1, engine(replay).drain(uid).sent)
        }

    @Test
    fun `typed fatal attempt refusal still quarantines`() =
        runTest {
            val store = FakeOutboxStore()
            store.seed(record(1, OutboxOperations.SUBMIT_ATTEMPT, 0))
            val summary =
                engine(store, transport = {
                    Result.failure(SyncFailure(SyncError.Refused("invalid attempt payload")))
                }).drain(uid)
            assertEquals(1, summary.quarantined)
        }

    @Test
    fun `same uid with changed session generation cannot persist or acknowledge late response`() =
        runTest {
            val receipts = FakeMutationReceiptStore()
            val store = FakeOutboxStore().also { it.seed(record(1), record(2)) }
            var sent = 0
            val summary =
                engine(store, receipts, transport = {
                    sent++
                    session = session.copy(generation = session.generation + 1)
                    Result.success(MutationResponse("{}"))
                }).drain(uid)
            assertEquals(1, sent)
            assertEquals(0, summary.sent)
            assertTrue(store.removed.isEmpty())
            assertEquals(null, receipts.find(uid, "mutation-1"))
        }

    @Test
    fun `generation changed during cached receipt read cannot ack`() =
        runTest {
            val row = record(1)
            val receipts =
                object : FakeMutationReceiptStore() {
                    override suspend fun find(
                        ownerUid: String,
                        mutationId: String,
                    ): MutationReceipt? {
                        session = session.copy(generation = 2)
                        return receipt(row)
                    }
                }
            val store = FakeOutboxStore().also { it.seed(row) }
            assertEquals(0, engine(store, receipts).drain(uid).sent)
            assertTrue(store.removed.isEmpty())
        }

    @Test
    fun `receipt cancellation escapes and leaves pending row`() =
        runTest {
            val store = FakeOutboxStore().also { it.seed(record(1)) }
            val receipts =
                object : FakeMutationReceiptStore() {
                    override suspend fun persist(receipt: MutationReceipt) {
                        throw CancellationException("stopped")
                    }
                }
            assertFailsWith<CancellationException> { engine(store, receipts).drain(uid) }
            assertTrue(store.removed.isEmpty())
        }

    @Test
    fun `configured page cap bounds a run without losing pending tail`() =
        runTest {
            val store = FakeOutboxStore()
            store.seed(*(1L..120L).map { record(it) }.toTypedArray())
            assertEquals(100, engine(store, maxBatches = 2).drain(uid).sent)
            assertEquals(20, store.all.size)
            assertEquals(20, engine(store).drain(uid).sent)
        }

    @Test
    fun `sign in waits until active receipt and ack finish under the same mutex`() =
        runTest {
            val entered = CompletableDeferred<Unit>()
            val proceed = CompletableDeferred<Unit>()
            val store = FakeOutboxStore().also { it.seed(record(1)) }
            val receipts =
                object : FakeMutationReceiptStore() {
                    override suspend fun persist(receipt: MutationReceipt) {
                        entered.complete(Unit)
                        proceed.await()
                        super.persist(receipt)
                    }
                }
            val engine = engine(store, receipts)
            val traces =
                object : OwnerTraces {
                    override val namedLosses = emptyList<OwnerDataKind>()

                    override suspend fun forget(uid: String) = Unit

                    override suspend fun ownersWithTraces() = emptyList<String>()

                    override suspend fun lossesOf(uid: String) = emptyList<OwnerDataKind>()
                }
            val guard = AccountSwitchGuard(engine, store, store, traces)
            var signedIn = false
            val pass = async { engine.drain(uid) }
            entered.await()
            val switch =
                async {
                    guard.switchAndForget(listOf(uid)) {
                        signedIn = true
                        "other"
                    }
                }
            yield()
            assertFalse(signedIn)
            proceed.complete(Unit)
            assertEquals(1, pass.await().sent)
            switch.await()
            assertTrue(signedIn)
        }
}
