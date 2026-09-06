package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.tpov.schoolquiz.shared.core.outbox.MutationReceipt
import com.tpov.schoolquiz.shared.core.outbox.OutboxOperations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BoxOpeningMarkerPersistenceTest {
    private lateinit var dir: File
    private lateinit var db: AppDatabase
    private val operation = OutboxOperations.OPEN_GIFT_BOX
    private val dao get() = db.outboxDao()

    @Before fun setUp() {
        dir = Files.createTempDirectory("box-markers").toFile()
        db = open()
    }

    @After fun tearDown() {
        db.close()
        dir.deleteRecursively()
    }

    private fun open() =
        Room.databaseBuilder<AppDatabase>(name = File(dir, "boxes.db").absolutePath)
            .setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.Unconfined)
            .addTypeConverter(StringSetConverter()).addTypeConverter(TopParticipantListConverter()).build()

    private fun delivery(
        id: String = "opening-00000001",
        owner: String = "A",
    ) = OutboxEntity(
        mutationId = OutboxOperations.mutationKey(operation, id), ownerUid = owner, operation = operation,
        payload = """{"ownerUid":"$owner","openingId":"$id"}""", entityRef = "economy.BOX_OPENING:$id",
        expectedVersion = null,
        state = "WAITING",
        attemptCount = 0,
        nextRetryAtMs = 0,
        lastError = null,
        createdAtMs = 1,
    )

    private fun marker(row: OutboxEntity) =
        row.copy(
            mutationId = row.mutationId + "-presentation",
            state = "SYNCHRONOUS",
        )

    private suspend fun receipt(row: OutboxEntity) =
        RoomMutationReceiptStore(db.mutationReceiptDao()).persist(
            MutationReceipt(row.ownerUid, row.mutationId, row.operation, row.entityRef, "{}", 2),
        )

    @Test fun markerAndDeliveryAreAtomicAndConcurrentCallsReuseOneOpening() =
        runTest {
            val first = delivery()
            val second = delivery("opening-00000002")
            val rows =
                coroutineScope {
                    val a = async { dao.reserveSynchronousIntent(first, marker(first)) }
                    val b = async { dao.reserveSynchronousIntent(second, marker(second)) }
                    listOf(a.await(), b.await())
                }
            assertEquals(rows[0], rows[1])
            assertEquals(1, dao.countPending("A"))
            assertEquals(1, dao.countRemainder("A").synchronous)
        }

    @Test fun failedDeliveryInsertRollsBackNewMarker() =
        runTest {
            val row = delivery()
            dao.insert(row)
            assertFailsWith<IllegalStateException> { dao.reserveSynchronousIntent(row, marker(row)) }
            assertNull(dao.findSynchronousIntent("A", operation))
            assertEquals(1, dao.countPending("A"))
        }

    @Test fun backgroundAckAndReopenDoNotLoseUndismissedResultOrResendIt() =
        runTest {
            val row = delivery()
            val saved = dao.reserveSynchronousIntent(row, marker(row))
            receipt(row)
            dao.delete(checkNotNull(dao.findByMutationId(row.mutationId)).id)
            db.close()
            db = open()
            assertEquals(saved, dao.findSynchronousIntent("A", operation))
            assertEquals(saved, dao.reserveSynchronousIntent(row, marker(row)))
            assertEquals(0, dao.countPending("A"))
            assertTrue(dao.acknowledgeSynchronousIntent("A", saved.id, row.mutationId))
            assertNull(dao.findSynchronousIntent("A", operation))
            assertNotNull(db.mutationReceiptDao().find("A", row.mutationId))
        }

    @Test fun uncertainAndWrongOwnerDismissalsDoNotEraseMarker() =
        runTest {
            val row = delivery()
            val saved = dao.reserveSynchronousIntent(row, marker(row))
            assertEquals(false, dao.acknowledgeSynchronousIntent("A", saved.id, row.mutationId))
            receipt(row)
            assertEquals(false, dao.acknowledgeSynchronousIntent("B", saved.id, row.mutationId))
            assertEquals(false, dao.acknowledgeSynchronousIntent("A", saved.id, "another-mutation"))
            assertNotNull(dao.findById(saved.id))
        }

    @Test fun newOpeningCannotConsumeHistoricalReceiptEvenWithReusedGeneratorId() =
        runTest {
            val row = delivery()
            receipt(row)
            assertFailsWith<IllegalStateException> { dao.reserveSynchronousIntent(row, marker(row)) }
            assertNull(dao.findSynchronousIntent("A", operation))
            assertEquals(0, dao.countPending("A"))
        }

    @Test fun ownersHaveIndependentActiveMarkersAndCleanupRemovesOnlyOneOwner() =
        runTest {
            val a = delivery()
            val b = delivery("opening-00000002", "B")
            dao.reserveSynchronousIntent(a, marker(a))
            dao.reserveSynchronousIntent(b, marker(b))
            dao.deleteAllOf("A")
            assertNull(dao.findSynchronousIntent("A", operation))
            assertNotNull(dao.findSynchronousIntent("B", operation))
            assertEquals(1, dao.countPending("B"))
        }

    @Test fun bothPermanentOperationsRespectOldRowsBackoffAndMarkersNeverBecomeDue() =
        runTest {
            val now = 365L * 86_400_000
            for ((index, op) in OutboxOperations.permanentReceiptOperations.withIndex()) {
                val row =
                    delivery("opening-0000000$index").copy(
                        operation = op,
                        entityRef = null,
                        mutationId = "mutation-$index",
                        nextRetryAtMs = now + 10,
                    )
                dao.insert(row)
            }
            val row = delivery("opening-marker-0001", "B")
            dao.reserveSynchronousIntent(row, marker(row))
            assertEquals(0, dao.due("A", now, 100, 50).size)
            assertEquals(0, dao.expired("A", now, 100, 50).size)
            assertEquals(2, dao.due("A", now + 10, 100, 50).size)
            assertEquals(listOf("WAITING"), dao.due("B", now, 100, 50).map { it.state })
        }
}
