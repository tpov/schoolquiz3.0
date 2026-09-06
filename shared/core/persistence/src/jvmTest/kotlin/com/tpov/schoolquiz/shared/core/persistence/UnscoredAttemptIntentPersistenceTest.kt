package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.Room
import androidx.room.useWriterConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.tpov.schoolquiz.shared.core.outbox.OutboxOperations
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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

/** Real Room transactions: an unscored request must never appear in scored history. */
class UnscoredAttemptIntentPersistenceTest {
    private lateinit var dir: File
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("unscored-attempt-intents").toFile()
        db = open()
    }

    private fun open(): AppDatabase =
        Room.databaseBuilder<AppDatabase>(name = File(dir, "raw-attempts.db").absolutePath)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .addTypeConverter(StringSetConverter())
            .addTypeConverter(TopParticipantListConverter())
            .build()

    @After
    fun tearDown() {
        db.close()
        dir.deleteRecursively()
    }

    private fun row(kind: String = "COMPLETED", owner: String = "uid-A") = OutboxEntity(
        mutationId = OutboxOperations.mutationKey(OutboxOperations.SUBMIT_ATTEMPT, "raw-1"),
        ownerUid = owner, operation = OutboxOperations.SUBMIT_ATTEMPT,
        payload = "{\"attemptId\":\"raw-1\",\"completionKind\":\"$kind\",\"answers\":[]}",
        entityRef = "lesson_runner:attempt:raw-1", expectedVersion = null, state = "WAITING",
        attemptCount = 0, nextRetryAtMs = 0, lastError = null, createdAtMs = 1000,
    )

    private suspend fun save(outbox: OutboxEntity = row(), owner: String = "uid-A") {
        db.lessonAttemptDao().saveUnscoredAttemptIntent("raw-1", owner, outbox)
    }

    private suspend fun assertNoFakeResult() {
        assertNull(db.lessonAttemptDao().findAttempt("raw-1"))
        assertTrue(db.lessonAttemptDao().findAttemptAnswers("raw-1").isEmpty())
    }

    @Test
    fun savesOnlyRawMarkerAndQueueThenPreservesExactRetryBackoff() = runTest {
        save()
        assertNoFakeResult()
        assertEquals(row().payload, db.lessonAttemptDao().findAttemptIntent("raw-1")!!.payload)
        val first = db.outboxDao().findByMutationId(row().mutationId)!!
        db.outboxDao().applyDecision(first.id, "WAITING", 9000, 4, "offline", null)
        save()
        val retried = db.outboxDao().findByMutationId(row().mutationId)!!
        assertEquals(first.id, retried.id)
        assertEquals(4, retried.attemptCount)
        assertEquals(9000L, retried.nextRetryAtMs)
        assertNoFakeResult()
    }

    @Test
    fun ackAndReopenKeepFirstBodyAndNeverReenqueueOrInventResult() = runTest {
        save()
        db.outboxDao().delete(db.outboxDao().findByMutationId(row().mutationId)!!.id)
        db.close()
        db = open()
        save()
        assertNull(db.outboxDao().findByMutationId(row().mutationId))
        assertNotNull(db.lessonAttemptDao().findAttemptIntent("raw-1"))
        assertTrue(runCatching { save(row("ABORTED")) }.exceptionOrNull() is AttemptIntentConflict)
        assertNoFakeResult()
    }

    @Test
    fun quarantineNeverRevivesEvenWithoutAScoredRow() = runTest {
        save()
        val queued = db.outboxDao().findByMutationId(row().mutationId)!!
        db.outboxDao().applyDecision(queued.id, "QUARANTINED", 0, 1, "invalid payload", null)
        db.lessonAttemptDao().rollbackAttempt("raw-1")
        assertTrue(runCatching { save() }.isFailure)
        assertEquals("QUARANTINED", db.outboxDao().findByMutationId(row().mutationId)!!.state)
        assertNotNull(db.lessonAttemptDao().findAttemptIntent("raw-1"))
        assertNoFakeResult()
    }

    @Test
    fun scoredLegacyAttemptOrOrphanAnswersCannotBecomeUnscored() = runTest {
        val previous = LessonAttemptEntity("raw-1", "uid-A", "lesson-1", 2, 1, "9", 100, 1000)
        db.lessonAttemptDao().saveAttemptWithAnswers(previous, emptyList(), emptyList())
        assertTrue(runCatching { save() }.exceptionOrNull() is AttemptIntentConflict)
        assertEquals(previous, db.lessonAttemptDao().findAttempt("raw-1"))
        assertNull(db.lessonAttemptDao().findAttemptIntent("raw-1"))
        assertNull(db.outboxDao().findByMutationId(row().mutationId))
        db.lessonAttemptDao().deleteAttempt("raw-1")
        val answer = QuestionAnswerEntity("raw-1", "q1", "uid-A", "lesson-1", 2, 1, 0, 9, "{}", 900, 100, 0)
        db.lessonAttemptDao().upsertAnswers(listOf(answer))
        assertTrue(runCatching { save() }.exceptionOrNull() is AttemptIntentConflict)
        assertEquals(listOf(answer), db.lessonAttemptDao().findAttemptAnswers("raw-1"))
        assertNull(db.lessonAttemptDao().findAttemptIntent("raw-1"))
        assertNull(db.outboxDao().findByMutationId(row().mutationId))
    }

    @Test
    fun queuedOnlyLegacyRequestIsNotPromotedIntoRawProtocol() = runTest {
        db.outboxDao().insert(row())
        assertTrue(runCatching { save() }.exceptionOrNull() is AttemptIntentConflict)
        assertNull(db.lessonAttemptDao().findAttemptIntent("raw-1"))
        assertEquals(row().payload, db.outboxDao().findByMutationId(row().mutationId)!!.payload)
        assertNoFakeResult()
    }

    @Test
    fun identityMismatchRejectsBeforeAnyWrite() = runTest {
        listOf(
            row(owner = "uid-B"), row().copy(operation = OutboxOperations.SUBMIT_RATING),
            row().copy(mutationId = "wrong"), row().copy(entityRef = "lesson_runner:attempt:wrong"),
        ).forEach {
            assertTrue(runCatching { save(it) }.exceptionOrNull() is AttemptIntentConflict)
        }
        assertNull(db.lessonAttemptDao().findAttemptIntent("raw-1"))
        assertNull(db.outboxDao().findByMutationId(row().mutationId))
        assertNoFakeResult()
        save()
        assertTrue(runCatching { save(row(owner = "uid-B"), owner = "uid-B") }.exceptionOrNull() is AttemptIntentConflict)
        assertEquals("uid-A", db.lessonAttemptDao().findAttemptIntent("raw-1")!!.ownerUid)
    }

    @Test
    fun newIntentCannotStartAlreadyTerminalOrSkipDispatch() = runTest {
        listOf("QUARANTINED", "SYNCHRONOUS", "IN_FLIGHT").forEach { state ->
            assertTrue(runCatching { save(row().copy(state = state)) }.exceptionOrNull() is AttemptIntentConflict)
            assertNull(db.lessonAttemptDao().findAttemptIntent("raw-1"))
            assertNull(db.outboxDao().findByMutationId(row().mutationId))
        }
    }

    @Test
    fun completeAndAbortRaceChooseOneFrozenBodyAndNoSuccessfulHistoryRow() = runTest {
        val gate = CompletableDeferred<Unit>()
        val attempts = listOf("COMPLETED", "ABORTED").map { kind ->
            async(Dispatchers.Default) { gate.await(); runCatching { save(row(kind)) } }
        }
        gate.complete(Unit)
        val results = attempts.awaitAll()
        assertEquals(1, results.count { it.isSuccess })
        assertEquals(1, results.count { it.exceptionOrNull() is AttemptIntentConflict })
        assertEquals(db.outboxDao().findByMutationId(row().mutationId)!!.payload,
            db.lessonAttemptDao().findAttemptIntent("raw-1")!!.payload)
        assertNoFakeResult()
    }

    @Test
    fun queueInsertionFailureRollsBackTheAlreadyInsertedMarker() = runTest {
        db.useWriterConnection { connection ->
            connection.usePrepared(
                "CREATE TRIGGER reject_raw_queue BEFORE INSERT ON outbox " +
                    "BEGIN SELECT RAISE(ABORT, 'injected queue failure'); END",
            ) { it.step() }
        }
        assertTrue(runCatching { save() }.isFailure)
        assertNull(db.lessonAttemptDao().findAttemptIntent("raw-1"))
        assertNull(db.outboxDao().findByMutationId(row().mutationId))
        assertNoFakeResult()
    }

    @Test
    fun scoredWriterCannotUpgradeRawIntentIntoSuccessfulHistoryAfterAck() = runTest {
        save()
        db.outboxDao().delete(db.outboxDao().findByMutationId(row().mutationId)!!.id)
        val invented = LessonAttemptEntity("raw-1", "uid-A", "lesson-1", 2, 1, "9", 100, 1000)
        assertTrue(runCatching {
            db.lessonAttemptDao().saveAttemptWithAnswers(invented, emptyList(), emptyList(), row())
        }.exceptionOrNull() is AttemptIntentConflict)
        assertNoFakeResult()
        assertNull(db.outboxDao().findByMutationId(row().mutationId))
    }
}
