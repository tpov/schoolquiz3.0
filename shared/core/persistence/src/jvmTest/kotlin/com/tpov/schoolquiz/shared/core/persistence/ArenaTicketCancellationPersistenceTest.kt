package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.Room
import androidx.room.useWriterConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.tpov.schoolquiz.shared.core.outbox.OutboxOperations
import com.tpov.schoolquiz.shared.core.outbox.OwnerForgetReason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Real Room rollback/reopen coverage. The fixture validator is NOT the future production status parser. */
class ArenaTicketCancellationPersistenceTest {
    private lateinit var directory: File
    private lateinit var db: AppDatabase
    private val dao get() = db.arenaTicketDao()
    private var nextId = 0

    @Before
    fun setUp() {
        directory = Files.createTempDirectory("arena-cancel-resolution").toFile()
        db = open()
    }

    private fun open() =
        Room.databaseBuilder<AppDatabase>(name = File(directory, "arena.db").absolutePath)
            .setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO)
            .addTypeConverter(StringSetConverter()).addTypeConverter(TopParticipantListConverter()).build()

    @After
    fun tearDown() {
        db.close()
        directory.deleteRecursively()
    }

    private data class Fixture(
        val ticket: ArenaTicketRecordEntity,
        val command: ArenaCommandEntity,
        val first: LessonAttemptIntentEntity,
        val pending: OutboxEntity,
        val proof: ArenaTicketRecordEntity,
    )

    private fun fixture(owner: String = "A"): Fixture {
        val id = (++nextId).toString().padStart(64, '0')
        val attempt = "attempt-$id"
        val ticket =
            ArenaTicketRecordEntity(
                owner, id, attempt, "request-$id", "{\"priceNolics\":10,\"lifeCost\":2,\"issuedAtMs\":100}",
                "unsafe bank is intentionally not consulted", "frozen-sha", "RESERVED", null,
                "LAUNCHED", "SETTLE", 100, "retained-final-checkpoint", 3,
            )
        val body = "{\"ownerUid\":\"$owner\",\"ticketId\":\"$id\",\"attemptId\":\"$attempt\",\"raw\":true}"
        val command = ArenaCommandEntity(owner, "SETTLE", attempt, id, attempt, body, sha(body), "PENDING", null, 150)
        val first =
            LessonAttemptIntentEntity(
                attempt,
                owner,
                OutboxOperations.mutationKey(OutboxOperations.SUBMIT_ATTEMPT, attempt),
                OutboxOperations.SUBMIT_ATTEMPT,
                body,
                "lesson_runner:attempt:$attempt",
                null,
            )
        val pending =
            OutboxEntity(
                mutationId = first.mutationId, ownerUid = owner, operation = first.operation, payload = body,
                entityRef = first.entityRef, expectedVersion = null, state = "WAITING", attemptCount = 7,
                nextRetryAtMs = 900, lastError = "TICKET_CANCELLED", createdAtMs = 150,
            )
        val proof =
            ticket.copy(
                snapshotJson = null, snapshotSha256 = null, serverStatus = "CANCELLED", terminalJson = terminal(id),
                localUse = "UNUSED", decision = "NONE", observedAtMs = 300,
                checkpointJson = null, checkpointRevision = 0,
            )
        return Fixture(ticket, command, first, pending, proof)
    }

    private fun terminal(id: String) =
        "{\"receiptId\":\"arena-cancel:$id\",\"cancelledAtMs\":200," +
            "\"releasedNolics\":10,\"releasedLifePoints\":2}"

    private fun sha(value: String) =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    private val validator =
        ArenaCancellationValidator { ticket, command, first, proof ->
            require(command.bodySha256 == sha(command.bodyJson) && first.payload == command.bodyJson)
            require(proof.terminalJson == terminal(ticket.ticketId))
        }

    private suspend fun seed(
        fixture: Fixture,
        marker: Boolean = true,
        queue: Boolean = true,
    ) {
        dao.insertTicket(fixture.ticket)
        dao.insertCommand(fixture.command)
        if (marker) dao.insertIntent(fixture.first)
        if (queue) dao.insertOutbox(fixture.pending)
    }

    private suspend fun resolve(fixture: Fixture) =
        dao.resolveCancelledSettlement(fixture.command, fixture.first, fixture.proof, validator)

    private suspend fun state(fixture: Fixture): List<Any?> =
        listOf(
            dao.ticket(fixture.ticket.ownerUid, fixture.ticket.ticketId),
            dao.ticketCommand(fixture.ticket.ownerUid, fixture.ticket.ticketId, "SETTLE"),
            dao.intent(fixture.first.attemptId),
            dao.queued(fixture.first.mutationId),
            dao.hasAnyReceipt(fixture.first.mutationId),
            dao.hasScoredRows(fixture.first.attemptId),
        )

    private suspend fun rejectsWithoutWrites(
        fixture: Fixture,
        block: suspend () -> Unit,
    ) {
        val before = state(fixture)
        assertTrue(runCatching { block() }.isFailure)
        assertEquals(before, state(fixture))
    }

    private suspend fun sql(statement: String) =
        db.useWriterConnection { connection -> connection.usePrepared(statement) { it.step() } }

    @Test
    fun commitsCancellationAndRetainedIntentWithoutRewardOrGenericAckAndDeletesOnlyExactDelivery() =
        runTest {
            val f = fixture()
            val other = fixture("B")
            seed(f)
            seed(other)
            val otherBefore = state(other)
            resolve(f)
            val ticket = dao.ticket("A", f.ticket.ticketId)!!
            assertEquals("CANCELLED", ticket.serverStatus)
            assertEquals(f.proof.terminalJson, ticket.terminalJson)
            assertEquals("SETTLE", ticket.decision)
            assertEquals(f.ticket.snapshotJson, ticket.snapshotJson)
            assertEquals(f.ticket.checkpointJson, ticket.checkpointJson)
            assertEquals(f.ticket.checkpointRevision, ticket.checkpointRevision)
            assertEquals(
                f.command.copy(state = ARENA_RESOLVED_CANCELLED),
                dao.ticketCommand("A", ticket.ticketId, "SETTLE"),
            )
            assertEquals(f.first, dao.intent(f.first.attemptId))
            assertNull(dao.queued(f.first.mutationId))
            assertFalse(dao.hasAnyReceipt(f.first.mutationId))
            assertFalse(dao.hasScoredRows(f.first.attemptId))
            assertEquals(otherBefore, state(other))
        }

    @Test
    fun waitingPreconditionAndPriorAttentionResolveUsingTheReReadCommand() =
        runTest {
            val f = fixture().let { it.copy(pending = it.pending.copy(state = "WAITING_PRECONDITION")) }
            seed(f)
            dao.recordFailure("A", "SETTLE", f.command.commandId, "TICKET_CANCELLED", true)
            var called = false
            dao.resolveCancelledSettlement(
                f.command,
                f.first,
                f.proof,
                ArenaCancellationValidator { ticket, command, first, proof ->
                    assertEquals("ATTENTION", command.state)
                    validator.validate(ticket, command, first, proof)
                    called = true
                },
            )
            assertTrue(called)
            assertNull(dao.queued(f.first.mutationId))
            assertEquals("TICKET_CANCELLED", dao.ticketCommand("A", f.ticket.ticketId, "SETTLE")!!.lastFailure)
        }

    @Test
    fun missingQueueAndFirstIntentAfterSwitchResolveWithoutEnqueueOrSnapshot() =
        runTest {
            val f = fixture().let { it.copy(ticket = it.ticket.copy(snapshotJson = null, checkpointJson = null)) }
            val other = fixture("B")
            seed(f)
            seed(other)
            val otherBefore = state(other)
            // AccountSwitchGuard clears the owner's queue before its separate personal traces.
            db.outboxDao().deleteAllOf("A")
            db.ownerRowsDao().forgetOwner("A", emptyList(), OwnerForgetReason.SWITCH)
            assertNull(dao.intent(f.first.attemptId))
            assertNull(dao.queued(f.first.mutationId))
            assertEquals(f.ticket, dao.ticket("A", f.ticket.ticketId))
            assertEquals(f.command, dao.ticketCommand("A", f.ticket.ticketId, "SETTLE"))
            resolve(f)
            assertEquals(f.first, dao.intent(f.first.attemptId))
            assertNull(dao.queued(f.first.mutationId))
            dao.restoreSettlement("A", f.ticket.ticketId, f.pending)
            assertNull(dao.queued(f.first.mutationId))
            assertEquals(otherBefore, state(other))
        }

    @Test
    fun missingQueueWithRetainedFirstIntentDoesNotReplaceOrResendIt() =
        runTest {
            val f = fixture()
            seed(f, queue = false)
            resolve(f)
            assertEquals(f.first, dao.intent(f.first.attemptId))
            assertNull(dao.queued(f.first.mutationId))
        }

    @Test
    fun deleteFailureRollsBackReceiptTombstoneAndRecreatedFirstIntent() =
        runTest {
            val f = fixture()
            seed(f, marker = false)
            sql("CREATE TRIGGER reject_cancel_delete BEFORE DELETE ON outbox BEGIN SELECT RAISE(ABORT, 'stop'); END")
            rejectsWithoutWrites(f) { resolve(f) }
            assertNull(dao.intent(f.first.attemptId))
            sql("DROP TRIGGER reject_cancel_delete")
            resolve(f)
            assertEquals(f.first, dao.intent(f.first.attemptId))
        }

    @Test
    fun changedRowBetweenValidatedReadAndDeleteFailsExactPredicateAndRollsBackEverything() =
        runTest {
            val f = fixture()
            seed(f, marker = false)
            sql(
                "CREATE TRIGGER change_cancel_pending AFTER UPDATE ON arena_ticket_records " +
                    "BEGIN UPDATE outbox SET payload = 'changed' WHERE mutation_id = '${f.first.mutationId}'; END",
            )
            rejectsWithoutWrites(f) { resolve(f) }
            assertEquals(f.pending.payload, dao.queued(f.first.mutationId)!!.payload)
        }

    @Test
    fun postCommitReplayAfterReopenEvictionAndCleanupIsReadOnlyAndCannotLoseTombstone() =
        runTest {
            val f = fixture()
            seed(f)
            resolve(f)
            dao.evictSnapshot("A", f.ticket.ticketId)
            db.ownerRowsDao().forgetOwner("A", emptyList(), OwnerForgetReason.SIGN_OUT)
            db.close()
            db = open()
            val before = state(f)
            for (table in listOf("arena_ticket_records", "arena_command_journal", "lesson_attempt_intents", "outbox")) {
                for (event in listOf("INSERT", "UPDATE", "DELETE")) {
                    sql(
                        "CREATE TRIGGER reject_${table}_$event BEFORE $event ON $table " +
                            "BEGIN SELECT RAISE(ABORT, 'replay must not write'); END",
                    )
                }
            }
            resolve(f.copy(proof = f.proof.copy(observedAtMs = 999)))
            dao.recordFailure("A", "SETTLE", f.command.commandId, "TICKET_CANCELLED", true)
            dao.restoreSettlement("A", f.ticket.ticketId, f.pending)
            assertEquals(before, state(f))
            val acknowledgment = runCatching { dao.acknowledgeObservation(f.proof, "SETTLE", f.command.commandId) }
            assertTrue(acknowledgment.exceptionOrNull() is ArenaJournalConflict)
            assertEquals(before, state(f))
            assertNull(dao.intent(f.first.attemptId))
            assertNull(dao.queued(f.first.mutationId))
        }

    @Test
    fun alteredOwnerTermsAttemptOrUnprovenObservationCannotEraseAnything() =
        runTest {
            val f = fixture()
            seed(f)
            val p = f.proof
            val invalid =
                listOf(
                    p.copy(ownerUid = "B"), p.copy(ticketId = "other"), p.copy(attemptId = "other"),
                    p.copy(requestId = "other"), p.copy(termsJson = "repriced"), p.copy(serverStatus = "SETTLED"),
                    p.copy(serverStatus = "RESERVED"), p.copy(terminalJson = null), p.copy(terminalJson = ""),
                    p.copy(terminalJson = "{\"status\":\"CANCELLED\"}"), p.copy(snapshotJson = "extra bank"),
                    p.copy(snapshotSha256 = "extra hash"), p.copy(decision = "CANCEL"), p.copy(localUse = "LAUNCHED"),
                    p.copy(
                        checkpointJson = "extra checkpoint",
                    ),
                    p.copy(checkpointRevision = 1), p.copy(observedAtMs = -1),
                )
            for (proof in invalid) rejectsWithoutWrites(f) { resolve(f.copy(proof = proof)) }
        }

    @Test
    fun changedOriginalCommandOrEveryFrozenIntentFieldFailsClosed() =
        runTest {
            val f = fixture()
            seed(f)
            val c = f.command
            for (command in listOf(
                c.copy(ownerUid = "B"),
                c.copy(commandKind = "CANCEL"),
                c.copy(commandId = "other"),
                c.copy(ticketId = "other"),
                c.copy(attemptId = "other"),
                c.copy(bodyJson = "changed"),
                c.copy(bodySha256 = "changed"),
                c.copy(createdAtMs = 999),
            )) {
                rejectsWithoutWrites(f) { resolve(f.copy(command = command)) }
            }
            val i = f.first
            for (intent in listOf(
                i.copy(ownerUid = "B"),
                i.copy(attemptId = "other"),
                i.copy(mutationId = "other"),
                i.copy(operation = "lesson_runner.OTHER"),
                i.copy(payload = "changed"),
                i.copy(entityRef = "lesson_runner:other"),
                i.copy(entityRef = null),
                i.copy(expectedVersion = 1),
            )) {
                rejectsWithoutWrites(f) { resolve(f.copy(first = intent)) }
            }
            rejectsWithoutWrites(f) {
                resolve(
                    f.copy(
                        command = c.copy(bodyJson = "changed", bodySha256 = sha("changed")),
                        first = i.copy(payload = "changed"),
                    ),
                )
            }
        }

    @Test
    fun differentStoredFirstIntentAndPendingBodyOrOwnerCannotBeOverwritten() =
        runTest {
            val f = fixture()
            seed(f, marker = false)
            dao.insertIntent(f.first.copy(payload = "different original"))
            rejectsWithoutWrites(f) { resolve(f) }
            val changes: List<(OutboxEntity) -> OutboxEntity> =
                listOf(
                    { it.copy(ownerUid = "B") },
                    { it.copy(payload = "changed") },
                    { it.copy(operation = "lesson_runner.OTHER") },
                    { it.copy(entityRef = "lesson_runner:other") },
                    { it.copy(entityRef = null) },
                    { it.copy(expectedVersion = 1) },
                )
            for (change in changes) {
                val candidate = fixture().let { it.copy(pending = change(it.pending)) }
                seed(candidate)
                rejectsWithoutWrites(candidate) { resolve(candidate) }
            }
        }

    @Test
    fun everyNonPendingStatePreservesTheAttentionOutcome() =
        runTest {
            for (status in listOf("QUARANTINED", "CONFLICT", "SYNCHRONOUS", "IN_FLIGHT", "ACKNOWLEDGED", "UNKNOWN")) {
                val f = fixture().let { it.copy(pending = it.pending.copy(state = status)) }
                seed(f)
                rejectsWithoutWrites(f) { resolve(f) }
            }
        }

    @Test
    fun anyGenericReceiptEvenForeignOwnerOrMalformedResultBlocksResolution() =
        runTest {
            for (owner in listOf("A", "B")) {
                val f = fixture()
                seed(f)
                val receipt = MutationReceiptEntity(owner, f.first.mutationId, "unknown", null, "opaque ACK", 250)
                db.mutationReceiptDao().persist(receipt)
                rejectsWithoutWrites(f) { resolve(f) }
                assertEquals(receipt, db.mutationReceiptDao().find(owner, f.first.mutationId))
            }
        }

    @Test
    fun settledContradictoryTerminalAcknowledgedCommandOrMissingCommandFailsClosed() =
        runTest {
            val alterations: List<(Fixture) -> Fixture> =
                listOf(
                    { it.copy(ticket = it.ticket.copy(serverStatus = "SETTLED", terminalJson = "settled")) },
                    { it.copy(ticket = it.ticket.copy(serverStatus = "CANCELLED", terminalJson = "different proof")) },
                    { it.copy(ticket = it.ticket.copy(serverStatus = "CANCELLED", terminalJson = null)) },
                    { it.copy(ticket = it.ticket.copy(terminalJson = "contradictory RESERVED receipt")) },
                    { it.copy(command = it.command.copy(state = "ACKNOWLEDGED")) },
                    { it.copy(command = it.command.copy(state = "UNKNOWN")) },
                )
            for (alter in alterations) {
                val f = alter(fixture())
                seed(f)
                rejectsWithoutWrites(f) { resolve(f) }
            }
            val missing = fixture()
            dao.insertTicket(missing.ticket)
            dao.insertOutbox(missing.pending)
            rejectsWithoutWrites(missing) { resolve(missing) }
        }

    @Test
    fun requiredValidatorFailureRollsBackAndTamperedSecondProofOrReintroducedQueueCannotResolveAgain() =
        runTest {
            val f = fixture()
            seed(f)
            rejectsWithoutWrites(f) {
                dao.resolveCancelledSettlement(
                    f.command,
                    f.first,
                    f.proof,
                    ArenaCancellationValidator { _, _, _, _ ->
                        error("invalid server proof")
                    },
                )
            }
            resolve(f)
            rejectsWithoutWrites(f) { resolve(f.copy(proof = f.proof.copy(terminalJson = terminal("other")))) }
            rejectsWithoutWrites(f) { resolve(f.copy(first = f.first.copy(payload = "changed"))) }
            dao.insertOutbox(f.pending)
            rejectsWithoutWrites(f) { resolve(f) }
        }

    @Test
    fun alreadyObservedCancellationCanResolveButScoredHistoryCannotBeSilentlyReclassified() =
        runTest {
            val f = fixture()
            seed(f)
            dao.observe(f.proof)
            resolve(f)
            assertEquals(ARENA_RESOLVED_CANCELLED, dao.ticketCommand("A", f.ticket.ticketId, "SETTLE")!!.state)
            val scored = fixture()
            seed(scored)
            db.lessonAttemptDao().upsert(
                LessonAttemptEntity(scored.first.attemptId, "A", "lesson", 1, 1, "9", 100, 200),
            )
            rejectsWithoutWrites(scored) { resolve(scored) }
        }
}
