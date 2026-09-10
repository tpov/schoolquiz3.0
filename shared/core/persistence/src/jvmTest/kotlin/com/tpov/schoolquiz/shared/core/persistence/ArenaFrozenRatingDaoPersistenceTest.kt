package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.tpov.schoolquiz.shared.core.outbox.OutboxOperations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Real SQLite proof for the retained aggregate; repository payload/owner wiring is tested elsewhere. */
class ArenaFrozenRatingDaoPersistenceTest {
    private lateinit var db: AppDatabase
    private val dao get() = db.arenaFrozenRatingDao()

    @Before
    fun setUp() {
        db =
            Room.inMemoryDatabaseBuilder<AppDatabase>()
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.Unconfined)
                .addTypeConverter(StringSetConverter())
                .addTypeConverter(TopParticipantListConverter())
                .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun concurrentDifferentTicketsForOneLesson_createOnlyOneIntentAndMarker() =
        runTest {
            seed("ticket-a")
            seed("ticket-b")

            val results =
                listOf("ticket-a", "ticket-b").map { ticket ->
                    async(Dispatchers.Default) { save(ticket, ratingId = "rating-$ticket", at = 100) }
                }.awaitAll()

            assertEquals(1, results.count { it is ArenaFrozenRatingSave.Created })
            assertEquals(1, results.count { it is ArenaFrozenRatingSave.ExistingUnverified })
            assertTrue(dao.hasSharedMarker(OWNER, LESSON))
            assertEquals(1, db.outboxDao().countPending(OWNER))
        }

    @Test
    fun retryLoadsTheFirstBodyAndRandomIdInsteadOfReplacingThem() =
        runTest {
            seed("ticket-a")
            val first = save("ticket-a", ratingId = "random-first", at = 100) as ArenaFrozenRatingSave.Created
            val retry = save("ticket-a", ratingId = "random-replacement", at = 200)

            val retained = retry as ArenaFrozenRatingSave.Retained
            assertEquals("random-first", retained.intent.ratingId)
            assertEquals(first.intent.payload, retained.intent.payload)
            assertEquals(first.intent.mutationId, retained.outbox!!.mutationId)
            assertEquals(1, db.outboxDao().countPending(OWNER))
        }

    @Test
    fun staleArenaQuarantineCannotDeleteTheNewerMarker() =
        runTest {
            seed("ticket-old")
            val old = save("ticket-old", ratingId = "old", at = 100) as ArenaFrozenRatingSave.Created
            assertTrue(old.intent.payload.contains("\"ticketId\":\"ticket-old\""))
            assertTrue(dao.rollbackExact(OWNER, "ticket-old", old.intent.mutationId, "old", old.intent.payload))
            assertFalse(db.lessonRatingLocalDao().hasSubmitted(OWNER, LESSON).first())

            seed("ticket-new")
            val newer = save("ticket-new", ratingId = "new", at = 200) as ArenaFrozenRatingSave.Created
            assertFalse(dao.rollbackExact(OWNER, "ticket-old", old.intent.mutationId, "old", old.intent.payload))
            assertEquals(newer.intent, dao.intent(OWNER, "ticket-new"))
            assertTrue(db.lessonRatingLocalDao().hasSubmitted(OWNER, LESSON).first())
        }

    @Test
    fun changedSettlementReceiptEvidenceRefusesTheWholeLocalWrite() =
        runTest {
            seed("ticket-proof")
            val failure =
                runCatching {
                    save("ticket-proof", ratingId = "proof", at = 100, receiptPayload = "{\"changed\":true}")
                }

            assertTrue(failure.isFailure)
            assertFalse(db.lessonRatingLocalDao().hasSubmitted(OWNER, LESSON).first())
            assertEquals(0, db.outboxDao().countPending(OWNER))
        }

    private suspend fun seed(ticket: String) {
        val attempt = "attempt-$ticket"
        db.arenaTicketDao().insertTicket(
            ArenaTicketRecordEntity(
                OWNER, ticket, attempt, "request-$ticket", "{}", null, null, "RESERVED", null,
                "LAUNCHED", "SETTLE", 10,
            ),
        )
        db.arenaTicketDao().insertCommand(
            ArenaCommandEntity(OWNER, "SETTLE", attempt, ticket, attempt, "{}", "sha-$ticket", "PENDING", null, 10),
        )
        db.mutationReceiptDao().persist(
            MutationReceiptEntity(
                OWNER,
                attemptMutation(attempt),
                OutboxOperations.SUBMIT_ATTEMPT,
                "lesson_runner:attempt:$attempt",
                "{\"result\":{\"receipts\":[]}}",
                11,
            ),
        )
    }

    private suspend fun save(
        ticket: String,
        ratingId: String,
        at: Long,
        receiptPayload: String = "{\"result\":{\"receipts\":[]}}",
    ): ArenaFrozenRatingSave {
        val attempt = "attempt-$ticket"
        val payload = "{\"ticketId\":\"$ticket\",\"ratingId\":\"$ratingId\",\"rating\":2}"
        val intent =
            ArenaFrozenRatingIntentEntity(
                OWNER, ticket, attempt, LESSON, ratingId, ratingMutation(ratingId),
                OutboxOperations.SUBMIT_RATING, payload, "lesson_runner:rating:$ratingId", at,
            )
        val outbox =
            OutboxEntity(
                mutationId = intent.mutationId, ownerUid = OWNER, operation = intent.operation,
                payload = payload, entityRef = intent.entityRef, expectedVersion = null, state = "WAITING",
                attemptCount = 0, nextRetryAtMs = 0, lastError = null, createdAtMs = at,
            )
        return dao.saveFirst(
            LessonRatingSubmittedLocalEntity(OWNER, LESSON, at),
            intent,
            outbox,
            ArenaFrozenRatingProof(
                OWNER, ticket, attempt, attemptMutation(attempt), "lesson_runner:attempt:$attempt", "{}", "sha-$ticket",
                receiptPayload, 11,
            ),
        )
    }

    private fun attemptMutation(attempt: String) =
        OutboxOperations.mutationKey(
            OutboxOperations.SUBMIT_ATTEMPT,
            attempt,
        )

    private fun ratingMutation(rating: String) = OutboxOperations.mutationKey(OutboxOperations.SUBMIT_RATING, rating)

    private companion object {
        const val OWNER = "player"
        const val LESSON = "lesson"
    }
}
