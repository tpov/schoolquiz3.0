package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.tpov.schoolquiz.shared.core.outbox.MutationReceipt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class MutationReceiptPersistenceTest {
    private lateinit var dir: File
    private lateinit var db: AppDatabase
    private lateinit var store: RoomMutationReceiptStore
    private val receipt =
        MutationReceipt(
            "uid-A",
            "mutation-1",
            "lesson_runner.SUBMIT_ATTEMPT",
            "lesson_runner:attempt:a-1",
            "{\"accepted\":true,\"unknownField\":{\"value\":null}}",
            901,
        )

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("mutation-receipts").toFile()
        db = open()
        store = RoomMutationReceiptStore(db.mutationReceiptDao())
    }

    private fun open(): AppDatabase =
        Room.databaseBuilder<AppDatabase>(name = File(dir, "receipts.db").absolutePath)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.Unconfined)
            .addTypeConverter(StringSetConverter())
            .addTypeConverter(TopParticipantListConverter())
            .build()

    @After
    fun tearDown() {
        db.close()
        dir.deleteRecursively()
    }

    @Test
    fun receiptSurvivesReopenWithEveryIdentityAndOpaquePayloadField() =
        runTest {
            store.persist(receipt)
            db.close()
            db = open()
            store = RoomMutationReceiptStore(db.mutationReceiptDao())
            val saved = store.find(receipt.ownerUid, receipt.mutationId)!!
            assertEquals(receipt.ownerUid, saved.ownerUid)
            assertEquals(receipt.mutationId, saved.mutationId)
            assertEquals(receipt.operation, saved.operation)
            assertEquals(receipt.entityRef, saved.entityRef)
            assertEquals(receipt.payloadJson, saved.payloadJson)
            assertEquals(receipt.receivedAtMs, saved.receivedAtMs)
        }

    @Test
    fun retryDoesNotReplaceOriginalArrivalTimeOrOpaqueResult() =
        runTest {
            store.persist(receipt)
            store.persist(receipt.copy(receivedAtMs = 999))
            assertEquals(901L, store.find(receipt.ownerUid, receipt.mutationId)!!.receivedAtMs)
            val failure = runCatching { store.persist(receipt.copy(payloadJson = "{}")) }.exceptionOrNull()
            assertTrue(failure is IllegalStateException)
            assertEquals(receipt.payloadJson, store.find(receipt.ownerUid, receipt.mutationId)!!.payloadJson)
        }

    @Test
    fun receiptKeyAndObservationAreScopedToOwnerAndCleanupKeepsOtherOwner() =
        runTest {
            store.persist(receipt)
            store.persist(receipt.copy(ownerUid = "uid-B", payloadJson = "{\"accepted\":false}"))
            assertEquals(listOf(receipt), store.observeEntity("uid-A", receipt.entityRef!!).first())
            val traces = RoomOwnerTraces(db.ownerRowsDao()) { emptyList() }
            assertTrue(traces.ownersWithTraces().containsAll(listOf("uid-A", "uid-B")))
            traces.forget("uid-A")
            assertNull(store.find("uid-A", receipt.mutationId))
            assertNotNull(store.find("uid-B", receipt.mutationId))
            assertEquals(emptyList<MutationReceipt>(), store.observeEntity("uid-A", receipt.entityRef!!).first())
        }
}
