package com.tpov.schoolquiz.shared.feature.lesson_runner.data.repository

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.tpov.schoolquiz.shared.core.outbox.MutationReceipt
import com.tpov.schoolquiz.shared.core.outbox.MutationReceiptStore
import com.tpov.schoolquiz.shared.core.outbox.OutboxOperations
import com.tpov.schoolquiz.shared.core.outbox.OutboxRecord
import com.tpov.schoolquiz.shared.core.outbox.OutboxSession
import com.tpov.schoolquiz.shared.core.outbox.OutboxState
import com.tpov.schoolquiz.shared.core.persistence.AppDatabase
import com.tpov.schoolquiz.shared.core.persistence.ArenaCommandEntity
import com.tpov.schoolquiz.shared.core.persistence.ArenaTicketRecordEntity
import com.tpov.schoolquiz.shared.core.persistence.LessonRatingSubmittedLocalEntity
import com.tpov.schoolquiz.shared.core.persistence.OutboxEntity
import com.tpov.schoolquiz.shared.core.persistence.RoomMutationReceiptStore
import com.tpov.schoolquiz.shared.core.persistence.StringSetConverter
import com.tpov.schoolquiz.shared.core.persistence.TopParticipantListConverter
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.ArenaJournalCodec
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.ArenaOwnerGate
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.outbox.AttemptSettlementReceiptParser
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.outbox.LessonResultQuarantineRollback
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaFrozenRatingState
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.RatingId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ServerAttemptReward
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ServerAttemptSettlement
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ServerAttemptSettlementState
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider.RatingIdProvider
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionProvider
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionStamp
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ServerAttemptSettlementRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.security.MessageDigest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class ArenaFrozenRatingRepositoryRoomTest {
    private lateinit var db: AppDatabase
    private lateinit var session: Session
    private lateinit var receipts: Receipts
    private lateinit var ids: Ids
    private lateinit var repository: ArenaFrozenRatingRepositoryImpl

    @Before
    fun setUp() {
        db =
            Room.inMemoryDatabaseBuilder<AppDatabase>().setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.Unconfined).addTypeConverter(StringSetConverter())
                .addTypeConverter(TopParticipantListConverter()).build()
        session = Session()
        receipts = Receipts(RoomMutationReceiptStore(db.mutationReceiptDao()))
        ids = Ids()
        repository =
            ArenaFrozenRatingRepositoryImpl(
                ArenaFrozenRatingDependencies(
                    db.arenaFrozenRatingDao(),
                    receipts,
                    Settlements(),
                    ArenaSessionProvider { session.stamp },
                    Gate(session),
                    ids,
                    object : Clock {
                        override fun now() = Instant.fromEpochMilliseconds(100)
                    },
                    ArenaJournalCodec(::sha),
                ),
            )
    }

    @After fun tearDown() = db.close()

    @Test
    fun retryKeepsFirstRandomIdAndBody() =
        runTest {
            seed()
            assertIs<ArenaFrozenRatingState.Queued>(repository.submit(session.stamp, TICKET, 2).getOrThrow())
            assertEquals(1, ids.calls)
            assertIs<ArenaFrozenRatingState.Queued>(repository.submit(session.stamp, TICKET, 3).getOrThrow())
            val intent = db.arenaFrozenRatingDao().intent(OWNER, TICKET)!!
            assertEquals("rating-1", intent.ratingId)
            assertEquals(
                2,
                kotlinx.serialization.json.Json.parseToJsonElement(
                    intent.payload,
                ).jsonObject["rating"]!!.jsonPrimitive.content.toInt(),
            )
            assertEquals(1, ids.calls)
        }

    @Test
    fun noSettlementReceiptLeavesNoMarkerOrQueuedRating() =
        runTest {
            seed(includeReceipt = false)
            check(repository.submit(session.stamp, TICKET, 2).isFailure)
            assertNull(db.arenaFrozenRatingDao().intent(OWNER, TICKET))
            assertEquals(false, db.lessonRatingLocalDao().hasSubmitted(OWNER, LESSON).first())
            assertEquals(0, db.outboxDao().countPending(OWNER))
        }

    @Test
    fun ownerAbaDuringReceiptReadFailsWithoutWriting() =
        runTest {
            seed()
            receipts.afterFind = { session.switchAwayAndBack() }
            check(repository.submit(ArenaSessionStamp(OWNER, 1), TICKET, 2).isFailure)
            assertNull(db.arenaFrozenRatingDao().intent(OWNER, TICKET))
        }

    @Test
    fun cancellationFromReceiptStoreEscapesUnchanged() =
        runTest {
            seed()
            receipts.failure = CancellationException("stop")
            assertFailsWith<CancellationException> { repository.submit(session.stamp, TICKET, 2) }
            assertNull(db.arenaFrozenRatingDao().intent(OWNER, TICKET))
        }

    @Test
    fun emittedTicketPayloadReachesExactArenaQuarantineRollback() =
        runTest {
            seed()
            repository.submit(session.stamp, TICKET, 2).getOrThrow()
            val intent = db.arenaFrozenRatingDao().intent(OWNER, TICKET)!!
            val old = db.outboxDao().findByMutationId(intent.mutationId)!!
            val rollback =
                LessonResultQuarantineRollback(
                    db.lessonAttemptDao(),
                    db.lessonRatingLocalDao(),
                    db.arenaFrozenRatingDao(),
                )
            rollback.ratings.onQuarantined(old.record())
            assertNull(db.arenaFrozenRatingDao().intent(OWNER, TICKET))
            assertEquals(false, db.lessonRatingLocalDao().hasSubmitted(OWNER, LESSON).first())

            repository.submit(session.stamp, TICKET, 3).getOrThrow()
            val newer = db.arenaFrozenRatingDao().intent(OWNER, TICKET)!!
            assertEquals("rating-2", newer.ratingId)
            rollback.ratings.onQuarantined(old.record())
            assertEquals(newer, db.arenaFrozenRatingDao().intent(OWNER, TICKET))
            assertEquals(true, db.lessonRatingLocalDao().hasSubmitted(OWNER, LESSON).first())
        }

    @Test
    fun observerAcceptsExactWrappedCallableRatingReceipt() =
        runTest {
            seed()
            repository.submit(session.stamp, TICKET, 2).getOrThrow()
            val intent = db.arenaFrozenRatingDao().intent(OWNER, TICKET)!!
            receipts.persist(ratingReceipt(intent.ratingId))

            val accepted = assertIs<ArenaFrozenRatingState.Accepted>(repository.observe(session.stamp, TICKET).first())

            assertEquals(2, accepted.rating)
        }

    @Test
    fun ordinaryRatingMarkerBlocksArenaBeforeANewIdIsProposed() =
        runTest {
            seed()
            val ordinary = ordinaryMarker(at = 21)
            db.lessonRatingLocalDao().submitWithOutbox(ordinary, ordinaryOutbox("ordinary-first", ordinary))

            assertIs<ArenaFrozenRatingState.ExistingUnverified>(
                repository.submit(session.stamp, TICKET, 2).getOrThrow(),
            )
            assertNull(db.arenaFrozenRatingDao().intent(OWNER, TICKET))
            assertEquals(0, ids.calls)
            assertEquals(true, db.arenaFrozenRatingDao().hasExactMarker(OWNER, LESSON, ordinary.submittedAt))
        }

    @Test
    fun ordinaryRatingCannotReplaceArenaMarkerOrQueueItsOwnRow() =
        runTest {
            seed()
            repository.submit(session.stamp, TICKET, 2).getOrThrow()
            val arena = db.arenaFrozenRatingDao().intent(OWNER, TICKET)!!
            val ordinary = ordinaryMarker(at = 22)
            val row = ordinaryOutbox("ordinary-after-arena", ordinary)

            val result = runCatching { db.lessonRatingLocalDao().submitWithOutbox(ordinary, row) }

            check(result.isFailure)
            assertEquals(arena, db.arenaFrozenRatingDao().intent(OWNER, TICKET))
            assertEquals(true, db.arenaFrozenRatingDao().hasExactMarker(OWNER, LESSON, arena.createdAtMs))
            assertNull(db.outboxDao().findByMutationId(row.mutationId))
        }

    @Test
    fun oldOrdinaryQuarantineCannotClearArenaMarker() =
        runTest {
            seed()
            repository.submit(session.stamp, TICKET, 2).getOrThrow()
            val arena = db.arenaFrozenRatingDao().intent(OWNER, TICKET)!!
            val rollback =
                LessonResultQuarantineRollback(
                    db.lessonAttemptDao(),
                    db.lessonRatingLocalDao(),
                    db.arenaFrozenRatingDao(),
                )

            rollback.ratings.onQuarantined(ordinaryRecord("ordinary-old"))

            assertEquals(arena, db.arenaFrozenRatingDao().intent(OWNER, TICKET))
            assertEquals(true, db.arenaFrozenRatingDao().hasExactMarker(OWNER, LESSON, arena.createdAtMs))
        }

    private suspend fun seed(includeReceipt: Boolean = true) {
        val body = settlementBody()
        db.arenaTicketDao().insertTicket(
            ArenaTicketRecordEntity(
                ownerUid = OWNER,
                ticketId = TICKET,
                attemptId = ATTEMPT,
                requestId = "request",
                termsJson = "{}",
                snapshotJson = null,
                snapshotSha256 = null,
                serverStatus = "RESERVED",
                terminalJson = null,
                localUse = "LAUNCHED",
                decision = "SETTLE",
                observedAtMs = 10,
            ),
        )
        db.arenaTicketDao().insertCommand(
            ArenaCommandEntity(OWNER, "SETTLE", ATTEMPT, TICKET, ATTEMPT, body, sha(body), "PENDING", null, 10),
        )
        if (includeReceipt) {
            val receipt = settlementReceipt()
            receipts.persist(receipt)
            assertEquals(
                true,
                AttemptSettlementReceiptParser().parse(receipt, settlementIdentity()).isConfirmedCompletion,
            )
        }
    }

    private fun settlementReceipt() =
        MutationReceipt(
            OWNER,
            OutboxOperations.mutationKey(OutboxOperations.SUBMIT_ATTEMPT, ATTEMPT),
            OutboxOperations.SUBMIT_ATTEMPT,
            "lesson_runner:attempt:$ATTEMPT",
            settlementReceiptPayload(),
            11,
        )

    private fun settlementBody() =
        listOf(
            "{\"ownerUid\":\"$OWNER\",\"userId\":\"$OWNER\",\"ticketId\":\"$TICKET\",",
            "\"attemptId\":\"$ATTEMPT\",\"lessonId\":\"$LESSON\",\"lessonVersion\":1,",
            "\"difficulty\":\"HARD\",\"scope\":\"public\",\"sourceShelf\":\"arena\",",
            "\"catalogId\":\"catalog\",\"questId\":\"quest\",\"sectionId\":\"section\",",
            "\"themeId\":\"theme\",\"completionKind\":\"COMPLETED\",\"completedAtMs\":10}",
        ).joinToString("")

    private fun settlementReceiptPayload() =
        listOf(
            "{\"result\":{\"receipts\":[{\"attemptId\":\"$ATTEMPT\",\"uid\":\"$OWNER\",",
            "\"schemaVersion\":1,\"status\":\"SETTLED\",\"receiptId\":\"r\",\"settledAtMs\":10,",
            "\"completed\":true,\"lessonId\":\"$LESSON\",\"lessonVersion\":1,",
            "\"codeAnswer\":\"9\",\"percentScore\":100,\"lifePointsSpent\":0,",
            "\"plasmaPointsSpent\":0,\"reward\":{\"skillPoints\":0,\"nolics\":0}}]}}",
        ).joinToString("")

    /** Exact submitMutation callable wrapper around applyQuestRatingEvents' arena-service receipt. */
    private fun ratingReceipt(ratingId: String) =
        MutationReceipt(
            OWNER,
            OutboxOperations.mutationKey(OutboxOperations.SUBMIT_RATING, ratingId),
            OutboxOperations.SUBMIT_RATING,
            "lesson_runner:rating:$ratingId",
            listOf(
                "{\"replayed\":false,\"result\":{\"accepted\":1,\"receipts\":[{",
                "\"schemaVersion\":1,\"mutationId\":\"$ratingId\",\"uid\":\"$OWNER\",",
                "\"operation\":\"RATE_LESSON\",\"status\":\"APPLIED\",\"result\":{",
                "\"lessonId\":\"$LESSON\",\"ratingId\":\"arena-rating:server-key\",\"acceptedAtMs\":11}}]}}",
            ).joinToString(""),
            12,
        )

    private fun ordinaryMarker(at: Long) = LessonRatingSubmittedLocalEntity(OWNER, LESSON, at)

    private fun ordinaryOutbox(
        ratingId: String,
        marker: LessonRatingSubmittedLocalEntity,
    ) = OutboxEntity(
        mutationId = OutboxOperations.mutationKey(OutboxOperations.SUBMIT_RATING, ratingId),
        ownerUid = OWNER,
        operation = OutboxOperations.SUBMIT_RATING,
        payload =
            "{\"ratingId\":\"$ratingId\",\"userId\":\"$OWNER\",\"lessonId\":\"$LESSON\",\"rating\":3}",
        entityRef = "lesson_runner:rating:$ratingId",
        expectedVersion = null,
        state = OutboxState.WAITING.name,
        attemptCount = 0,
        nextRetryAtMs = 0,
        lastError = null,
        createdAtMs = marker.submittedAt,
    )

    private fun ordinaryRecord(ratingId: String) =
        OutboxRecord(
            id = 55,
            mutationId = OutboxOperations.mutationKey(OutboxOperations.SUBMIT_RATING, ratingId),
            ownerUid = OWNER,
            operation = OutboxOperations.SUBMIT_RATING,
            payload =
                "{\"ratingId\":\"$ratingId\",\"userId\":\"$OWNER\",\"lessonId\":\"$LESSON\",\"rating\":3}",
            entityRef = "lesson_runner:rating:$ratingId",
            state = OutboxState.QUARANTINED,
            createdAtMs = 19,
        )

    private fun settlementIdentity() =
        com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptIdentity(
            com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId(ATTEMPT),
            OWNER,
            com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId(LESSON),
            1,
            com.tpov.schoolquiz.shared.core.question_schema.Difficulty.HARD,
        )

    private class Session {
        var stamp = ArenaSessionStamp(OWNER, 1)

        fun switchAwayAndBack() {
            stamp = ArenaSessionStamp("another-owner", 2)
            stamp = ArenaSessionStamp(OWNER, 3)
        }
    }

    private class Gate(private val session: Session) : ArenaOwnerGate {
        override fun capture(owner: String) = OutboxSession(session.stamp.ownerUid, session.stamp.generation)

        override suspend fun <T> withOwner(
            session: OutboxSession,
            block: suspend () -> T,
        ): T {
            check(session == OutboxSession(this.session.stamp.ownerUid, this.session.stamp.generation))
            return block().also {
                check(
                    session == OutboxSession(this.session.stamp.ownerUid, this.session.stamp.generation),
                )
            }
        }
    }

    private class Ids : RatingIdProvider {
        var calls = 0

        override fun provide(
            userId: String,
            lessonId: com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId,
        ) = RatingId(
            "rating-${++calls}",
        )
    }

    private class Settlements : ServerAttemptSettlementRepository {
        override fun observe(
            attempt: com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptIdentity,
        ): Flow<ServerAttemptSettlement> =
            flowOf(
                ServerAttemptSettlement(
                    ServerAttemptSettlementState.SETTLED, "SETTLED", "r",
                    com.tpov.schoolquiz.shared.core.scoring.CodeAnswer(
                        "9",
                    ),
                    com.tpov.schoolquiz.shared.core.scoring.PercentScore(
                        100,
                    ),
                    true, 10, 0, 0, ServerAttemptReward(0, 0), true,
                ),
            )
    }

    private class Receipts(private val delegate: MutationReceiptStore) : MutationReceiptStore {
        var failure: CancellationException? = null
        var afterFind: suspend () -> Unit = {}

        override suspend fun persist(receipt: MutationReceipt) = delegate.persist(receipt)

        override suspend fun find(
            ownerUid: String,
            mutationId: String,
        ): MutationReceipt? {
            failure?.let { throw it }
            val value = delegate.find(ownerUid, mutationId)
            afterFind()
            return value
        }

        override fun observeEntity(
            ownerUid: String,
            entityRef: String,
        ): Flow<List<MutationReceipt>> =
            delegate.observeEntity(
                ownerUid,
                entityRef,
            )
    }

    private companion object {
        const val OWNER = "player"
        const val TICKET = "ticket"
        const val ATTEMPT = "attempt"
        const val LESSON = "lesson"
    }
}

private fun com.tpov.schoolquiz.shared.core.persistence.OutboxEntity.record() =
    OutboxRecord(id, mutationId, ownerUid, operation, payload, OutboxState.valueOf(state), createdAtMs, entityRef)

private fun sha(value: String): String =
    MessageDigest.getInstance(
        "SHA-256",
    ).digest(value.toByteArray()).joinToString("") {
        (it.toInt() and 0xff).toString(16).padStart(2, '0')
    }
