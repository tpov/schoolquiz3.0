package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.tpov.schoolquiz.shared.core.outbox.OutboxOperations
import com.tpov.schoolquiz.shared.core.outbox.OwnerForgetReason
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** Real SQLite transactions/reopen, not an in-memory model of the database invariants. */
class ArenaTicketJournalPersistenceTest {
    private lateinit var directory: File
    private lateinit var db: AppDatabase
    private val dao get() = db.arenaTicketDao()

    @Before
    fun setUp() {
        directory = Files.createTempDirectory("arena-journal").toFile()
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

    private fun ticket(
        id: String = "ticket",
        owner: String = "A",
    ) = ArenaTicketRecordEntity(
        owner, id, "attempt-$id", "request-$id", "{\"immutablePrice\":10}", "{\"questions\":[]}", "frozen-sha",
        "RESERVED", null, "UNUSED", "NONE", 100,
    )

    private fun consent(
        id: String = "prepare",
        body: String = "{\"ceiling\":10}",
    ) = ArenaCommandEntity("A", "PREPARE", id, null, null, body, "consent-sha-$body", "PENDING", null, 100)

    private fun settlement(
        row: ArenaTicketRecordEntity = ticket(),
        body: String = "{\"raw\":true}",
    ) = ArenaCommandEntity(
        row.ownerUid, "SETTLE", row.attemptId, row.ticketId, row.attemptId,
        body, "body-sha", "PENDING", null, 200,
    )

    private fun cancellation(row: ArenaTicketRecordEntity = ticket()) =
        ArenaCommandEntity(
            row.ownerUid, "CANCEL", "cancel", row.ticketId, row.attemptId,
            "{\"cancel\":true}", "cancel-sha", "PENDING", null, 200,
        )

    private fun outbox(cmd: ArenaCommandEntity = settlement()) =
        OutboxEntity(
            mutationId = OutboxOperations.mutationKey(OutboxOperations.SUBMIT_ATTEMPT, cmd.commandId),
            ownerUid = cmd.ownerUid, operation = OutboxOperations.SUBMIT_ATTEMPT, payload = cmd.bodyJson,
            entityRef = "lesson_runner:attempt:${cmd.attemptId}", expectedVersion = null, state = "WAITING",
            attemptCount = 0, nextRetryAtMs = 0, lastError = null, createdAtMs = cmd.createdAtMs,
        )

    // Deliberate test validator only: exercises transactional validation, not feature DTO parsing.
    private val checkpointValidator =
        ArenaCheckpointValidator { _, previous, candidate, completion ->
            val index = candidate.toInt()
            require(index in 0..1)
            if (previous == null) require(index == 0) else require(index >= previous.toInt())
            if (completion != null) require(index == 1)
        }

    private suspend fun claim(
        owner: String,
        id: String,
    ) = dao.claim(owner, id, "0", checkpointValidator)

    private suspend fun freezeSettlement(
        command: ArenaCommandEntity,
        row: OutboxEntity,
    ) {
        val ticket = dao.ticket(command.ownerUid, command.ticketId!!)!!
        if (ticket.checkpointJson == "0") {
            dao.saveCheckpoint(
                ticket.ownerUid,
                ticket.ticketId,
                ticket.attemptId,
                ticket.checkpointRevision,
                "1",
                checkpointValidator,
            )
        }
        dao.freezeSettlement(command, row, checkpointValidator)
    }

    private suspend fun freeze() {
        dao.observe(ticket())
        assertTrue(claim("A", "ticket"))
        freezeSettlement(settlement(), outbox())
    }

    @Test
    fun immutableConsentIsNotRepricedAndIdenticalReplayPreservesFailure() =
        runTest {
            dao.saveConsent(consent())
            dao.recordFailure("A", "PREPARE", "prepare", "RETRY_REGEN", false)
            dao.saveConsent(consent().copy(createdAtMs = 900))
            assertEquals("RETRY_REGEN", dao.command("A", "PREPARE", "prepare")!!.lastFailure)
            assertTrue(
                runCatching {
                    dao.saveConsent(consent(body = "{\"ceiling\":100}"))
                }.exceptionOrNull() is ArenaJournalConflict,
            )
            assertEquals(consent().bodyJson, dao.command("A", "PREPARE", "prepare")!!.bodyJson)
        }

    @Test
    fun pendingUnknownPreparesReserveBoundedCacheCapacityWithoutEvictingFinancialIdentity() =
        runTest {
            repeat(20) { dao.saveConsent(consent("p-$it")) }
            assertTrue(runCatching { dao.saveConsent(consent("overflow")) }.exceptionOrNull() is ArenaJournalConflict)
            dao.saveConsent(consent("p-0"))
            assertEquals(20, dao.pendingPrepareCount("A", "none"))
            assertNull(dao.command("A", "PREPARE", "overflow"))
        }

    @Test
    fun terminalNeverRegressesAndContradictoryFrozenTermsFail() =
        runTest {
            dao.observe(ticket())
            dao.observe(ticket().copy(serverStatus = "CANCELLED", terminalJson = "cancel-receipt"))
            dao.observe(ticket().copy(observedAtMs = 999))
            assertEquals("CANCELLED", dao.ticket("A", "ticket")!!.serverStatus)
            assertFalse(claim("A", "ticket"))
            assertTrue(
                runCatching {
                    dao.observe(ticket().copy(termsJson = "changed-price"))
                }.exceptionOrNull() is ArenaJournalConflict,
            )
            assertTrue(
                runCatching {
                    dao.observe(ticket().copy(serverStatus = "SETTLED"))
                }.exceptionOrNull() is ArenaJournalConflict,
            )
        }

    @Test
    fun headerOnlyIsCancellableButNeverLaunchable() =
        runTest {
            dao.observe(ticket().copy(snapshotJson = null, snapshotSha256 = null))
            assertFalse(claim("A", "ticket"))
            dao.chooseCancel(cancellation())
            assertEquals("CANCEL", dao.ticket("A", "ticket")!!.decision)
            assertEquals("RESERVED", dao.ticket("A", "ticket")!!.serverStatus)
            assertNull(dao.ticket("A", "ticket")!!.terminalJson)
        }

    @Test
    fun launchClaimSurvivesReopenAndIsNotATerminalDecisionOrFreshSecondRun() =
        runTest {
            dao.observe(ticket())
            assertTrue(claim("A", "ticket"))
            db.close()
            db = open()
            assertEquals("NONE", dao.ticket("A", "ticket")!!.decision)
            assertEquals("LAUNCHED", dao.ticket("A", "ticket")!!.localUse)
            assertFalse(claim("A", "ticket"))
            assertFalse(dao.evictSnapshot("A", "ticket"))
            dao.chooseCancel(cancellation())
        }

    @Test
    fun invalidOrOversizeInitialCheckpointCannotConsumeTheLaunchClaim() =
        runTest {
            dao.observe(ticket())
            assertTrue(runCatching { dao.claim("A", "ticket", "1", checkpointValidator) }.isFailure)
            var invoked = false
            val permissive = ArenaCheckpointValidator { _, _, _, _ -> invoked = true }
            assertTrue(runCatching { dao.claim("A", "ticket", "x".repeat(1_048_577), permissive) }.isFailure)
            assertFalse(invoked)
            assertEquals("UNUSED", dao.ticket("A", "ticket")!!.localUse)
            assertNull(dao.ticket("A", "ticket")!!.checkpointJson)
            assertTrue(claim("A", "ticket"))
            assertEquals("0", dao.ticket("A", "ticket")!!.checkpointJson)
        }

    @Test
    fun checkpointCompareAndSetBindsOwnerAttemptRevisionAndPreservesValidatedPrefix() =
        runTest {
            dao.observe(ticket())
            claim("A", "ticket")
            assertFalse(dao.saveCheckpoint("B", "ticket", ticket().attemptId, 0, "1", checkpointValidator))
            assertFalse(dao.saveCheckpoint("A", "ticket", "different-attempt", 0, "1", checkpointValidator))
            assertFalse(dao.saveCheckpoint("A", "ticket", ticket().attemptId, 7, "1", checkpointValidator))
            val start = CompletableDeferred<Unit>()
            val results =
                List(2) {
                    async(Dispatchers.Default) {
                        start.await()
                        dao.saveCheckpoint("A", "ticket", ticket().attemptId, 0, "1", checkpointValidator)
                    }
                }
            start.complete(Unit)
            assertEquals(1, results.awaitAll().count { it })
            assertTrue(
                runCatching {
                    dao.saveCheckpoint("A", "ticket", ticket().attemptId, 1, "0", checkpointValidator)
                }.isFailure,
            )
            db.close()
            db = open()
            assertEquals("1", dao.ticket("A", "ticket")!!.checkpointJson)
            assertEquals(1L, dao.ticket("A", "ticket")!!.checkpointRevision)
            assertFalse(dao.saveCheckpoint("A", "ticket", ticket().attemptId, 0, "1", checkpointValidator))
        }

    @Test
    fun launchedLegacyRecordWithoutCheckpointRequiresReconciliationAndCannotFreeze() =
        runTest {
            dao.insertTicket(ticket().copy(localUse = "LAUNCHED"))
            assertFalse(claim("A", "ticket"))
            assertFalse(dao.saveCheckpoint("A", "ticket", ticket().attemptId, 0, "1", checkpointValidator))
            assertTrue(runCatching { dao.freezeSettlement(settlement(), outbox(), checkpointValidator) }.isFailure)
            assertNull(dao.ticketCommand("A", "ticket", "SETTLE"))
            assertNull(dao.intent(ticket().attemptId))
            assertNull(dao.queued(outbox().mutationId))
        }

    @Test
    fun onlyAlreadyDurableFinalCheckpointCanFreezeTheExactCompletionInOneTransaction() =
        runTest {
            dao.observe(ticket())
            claim("A", "ticket")
            assertTrue(runCatching { dao.freezeSettlement(settlement(), outbox(), checkpointValidator) }.isFailure)
            assertNull(dao.ticketCommand("A", "ticket", "SETTLE"))
            assertNull(dao.intent(ticket().attemptId))
            assertNull(dao.queued(outbox().mutationId))
            assertEquals("NONE", dao.ticket("A", "ticket")!!.decision)
            dao.saveCheckpoint("A", "ticket", ticket().attemptId, 0, "1", checkpointValidator)
            val exactBody =
                ArenaCheckpointValidator { binding, previous, candidate, completion ->
                    assertEquals(ArenaCheckpointBinding("A", "ticket", ticket().attemptId, "frozen-sha"), binding)
                    assertEquals("1", previous)
                    assertEquals(previous, candidate)
                    require(completion == settlement().bodyJson)
                }
            val changed = settlement(body = "different")
            assertTrue(runCatching { dao.freezeSettlement(changed, outbox(changed), exactBody) }.isFailure)
            assertNull(dao.ticketCommand("A", "ticket", "SETTLE"))
            dao.freezeSettlement(settlement(), outbox(), exactBody)
            assertEquals("SETTLE", dao.ticket("A", "ticket")!!.decision)
            assertEquals(settlement().bodyJson, dao.queued(outbox().mutationId)!!.payload)
            assertFalse(dao.saveCheckpoint("A", "ticket", ticket().attemptId, 1, "1", checkpointValidator))
        }

    @Test
    fun concurrentCancelAndSettleHaveOnlyOneDurableWinner() =
        runTest {
            dao.observe(ticket())
            claim("A", "ticket")
            val start = CompletableDeferred<Unit>()
            val jobs =
                listOf(
                    async(Dispatchers.Default) {
                        start.await()
                        runCatching { dao.chooseCancel(cancellation()) }
                    },
                    async(Dispatchers.Default) {
                        start.await()
                        runCatching { freezeSettlement(settlement(), outbox()) }
                    },
                )
            start.complete(Unit)
            assertEquals(1, jobs.awaitAll().count { it.isSuccess })
            val decision = dao.ticket("A", "ticket")!!.decision
            assertTrue(decision == "CANCEL" || decision == "SETTLE")
            assertEquals(decision == "SETTLE", dao.queued(outbox().mutationId) != null)
            assertNull(db.lessonAttemptDao().findAttempt(ticket().attemptId))
            assertTrue(db.lessonAttemptDao().findAttemptAnswers(ticket().attemptId).isEmpty())
        }

    @Test
    fun settlementFreezeDoesNotRewriteFirstBodyOrBackoffAndAckDoesNotReenqueue() =
        runTest {
            freeze()
            val queued = dao.queued(outbox().mutationId)!!
            db.outboxDao().applyDecision(queued.id, "WAITING", 9999, 9, "offline", null)
            freezeSettlement(settlement(), outbox())
            assertEquals(9, dao.queued(outbox().mutationId)!!.attemptCount)
            val changed = settlement(body = "{\"raw\":false}")
            assertTrue(
                runCatching {
                    freezeSettlement(
                        changed,
                        outbox(changed),
                    )
                }.exceptionOrNull() is ArenaJournalConflict,
            )
            db.outboxDao().delete(queued.id)
            db.close()
            db = open()
            freezeSettlement(settlement(), outbox())
            assertNull(dao.queued(outbox().mutationId))
            assertNotNull(dao.ticketCommand("A", "ticket", "SETTLE"))
        }

    @Test
    fun ordinarySwitchPurgesLegacyRowsButRetainedBodyCanRestoreExactOwnerQueueAfterReopen() =
        runTest {
            freeze()
            db.outboxDao().delete(dao.queued(outbox().mutationId)!!.id)
            db.ownerRowsDao().forgetOwner("A", emptyList(), OwnerForgetReason.SWITCH)
            assertNull(dao.intent(ticket().attemptId))
            assertNotNull(dao.ticket("A", "ticket"))
            db.close()
            db = open()
            assertTrue(runCatching { dao.restoreSettlement("B", "ticket", outbox()) }.isFailure)
            dao.restoreSettlement("A", "ticket", outbox())
            assertEquals(outbox().payload, dao.queued(outbox().mutationId)!!.payload)
            assertEquals(ticket().attemptId, dao.intent(ticket().attemptId)!!.attemptId)
            assertEquals("SETTLE", dao.ticket("A", "ticket")!!.decision)
        }

    @Test
    fun quarantineDoesNotReviveQueueAndRemainsAttentionAfterQueueRemoval() =
        runTest {
            freeze()
            val row = dao.queued(outbox().mutationId)!!
            db.outboxDao().applyDecision(row.id, "QUARANTINED", 999, 4, "invalid", null)
            dao.restoreSettlement("A", "ticket", outbox())
            assertEquals("QUARANTINED", dao.queued(row.mutationId)!!.state)
            assertEquals("ATTENTION", dao.ticketCommand("A", "ticket", "SETTLE")!!.state)
            db.outboxDao().delete(row.id)
            dao.restoreSettlement("A", "ticket", outbox())
            assertNull(dao.queued(row.mutationId))
        }

    @Test
    fun explicitEraseStillErasesBothNewTablesAndNoReasonDefaultsToErase() =
        runTest {
            freeze()
            dao.saveConsent(consent())
            db.ownerRowsDao().forgetOwner("A", emptyList(), OwnerForgetReason.SIGN_OUT)
            assertNotNull(dao.ticket("A", "ticket"))
            db.ownerRowsDao().forgetOwner("A", emptyList())
            assertNull(dao.ticket("A", "ticket"))
            assertNull(dao.command("A", "PREPARE", "prepare"))
            assertTrue(db.ownerRowsDao().ownersWithArenaFinancialRecords().isEmpty())
            dao.observe(ticket())
            dao.saveConsent(consent())
            db.ownerRowsDao().forgetOwner("A", emptyList(), OwnerForgetReason.DELETE_ACCOUNT)
            assertNull(dao.ticket("A", "ticket"))
            assertNull(dao.command("A", "PREPARE", "prepare"))
        }

    @Test
    fun legacyAttemptMarkerCannotBecomeANewArenaCompletion() =
        runTest {
            dao.observe(ticket())
            claim("A", "ticket")
            db.lessonAttemptDao().insertAttemptIntent(outbox().attemptIntent(ticket().attemptId))
            assertTrue(
                runCatching { freezeSettlement(settlement(), outbox()) }.exceptionOrNull() is ArenaJournalConflict,
            )
            assertNull(dao.ticketCommand("A", "ticket", "SETTLE"))
            assertEquals("NONE", dao.ticket("A", "ticket")!!.decision)
        }

    @Test
    fun snapshotEvictionPreservesHashUseAndTerminalIdentity() =
        runTest {
            dao.observe(ticket())
            assertTrue(dao.evictSnapshot("A", "ticket"))
            assertEquals("frozen-sha", dao.ticket("A", "ticket")!!.snapshotSha256)
            assertTrue(
                runCatching {
                    dao.observe(ticket().copy(snapshotSha256 = "new-bank"))
                }.exceptionOrNull() is ArenaJournalConflict,
            )
            dao.observe(ticket())
            assertTrue(claim("A", "ticket"))
            dao.observe(ticket().copy(serverStatus = "SETTLED", terminalJson = "receipt"))
            assertTrue(dao.evictSnapshot("A", "ticket"))
            assertEquals("LAUNCHED", dao.ticket("A", "ticket")!!.localUse)
            assertFalse(claim("A", "ticket"))
        }

    @Test
    fun existingReceiptPreventsRestorationAfterAckButDoesNotInventTerminalMeaning() =
        runTest {
            freeze()
            val row = dao.queued(outbox().mutationId)!!
            db.mutationReceiptDao().persist(
                MutationReceiptEntity(
                    "A",
                    row.mutationId,
                    row.operation,
                    row.entityRef,
                    "{\"server\":\"opaque\"}",
                    500,
                ),
            )
            db.outboxDao().delete(row.id)
            dao.restoreSettlement("A", "ticket", outbox())
            assertNull(dao.queued(row.mutationId))
            assertEquals("RESERVED", dao.ticket("A", "ticket")!!.serverStatus)
            assertEquals("PENDING", dao.ticketCommand("A", "ticket", "SETTLE")!!.state)
        }

    @Test
    fun replayConfirmationAfterTerminalEvictionIsReadOnlyAndNeverCreatesAMissingIntent() =
        runTest {
            dao.observe(ticket())
            assertTrue(runCatching { dao.confirmSettlementReplay(settlement(), outbox()) }.isFailure)
            assertNull(dao.queued(outbox().mutationId))
            claim("A", "ticket")
            freezeSettlement(settlement(), outbox())
            dao.observe(ticket().copy(serverStatus = "SETTLED", terminalJson = "receipt"))
            dao.evictSnapshot("A", "ticket")
            val oldTicket = dao.ticket("A", "ticket")!!
            val oldCommand = dao.ticketCommand("A", "ticket", "SETTLE")!!
            val oldQueue = dao.queued(outbox().mutationId)!!
            dao.confirmSettlementReplay(settlement(), outbox())
            val changed = settlement(body = "changed")
            assertTrue(runCatching { dao.confirmSettlementReplay(changed, outbox(changed)) }.isFailure)
            assertEquals(oldTicket, dao.ticket("A", "ticket"))
            assertEquals(oldCommand, dao.ticketCommand("A", "ticket", "SETTLE"))
            assertEquals(oldQueue, dao.queued(outbox().mutationId))
            db.outboxDao().delete(oldQueue.id)
            db.close()
            db = open()
            dao.confirmSettlementReplay(settlement(), outbox())
            assertNull(dao.queued(outbox().mutationId))
            assertEquals(oldTicket, dao.ticket("A", "ticket"))
        }

    @Test
    fun failedQueueInsertRollsBackJournalMarkerAndDecisionAtomically() =
        runTest {
            dao.observe(ticket())
            claim("A", "ticket")
            val unrelated = outbox(settlement(ticket("unrelated"))).copy(id = 7)
            dao.insertOutbox(unrelated)
            assertTrue(runCatching { freezeSettlement(settlement(), outbox().copy(id = 7)) }.isFailure)
            assertNull(dao.ticketCommand("A", "ticket", "SETTLE"))
            assertNull(dao.intent(ticket().attemptId))
            assertNull(dao.queued(outbox().mutationId))
            assertEquals("NONE", dao.ticket("A", "ticket")!!.decision)
            assertEquals(unrelated, dao.queued(unrelated.mutationId))
        }

    @Test
    fun failedAcknowledgmentDoesNotLeaveAnUnpairedTicketObservation() =
        runTest {
            assertTrue(runCatching { dao.acknowledgeObservation(ticket(), "PREPARE", "missing") }.isFailure)
            assertNull(dao.ticket("A", "ticket"))
        }

    @Test
    fun boundedRecoveryKeepsOverflowHeaderCancellableAndCanRefetchAfterExplicitEviction() =
        runTest {
            repeat(20) { dao.observe(ticket("cached-$it")) }
            dao.observe(ticket("overflow"))
            assertEquals(20, dao.snapshotCount("A"))
            assertNull(dao.ticket("A", "overflow")!!.snapshotJson)
            assertEquals("frozen-sha", dao.ticket("A", "overflow")!!.snapshotSha256)
            assertFalse(claim("A", "overflow"))
            dao.evictSnapshot("A", "cached-0")
            dao.observe(ticket("overflow"))
            assertNotNull(dao.ticket("A", "overflow")!!.snapshotJson)
            dao.chooseCancel(cancellation(ticket("overflow")))
            assertEquals("RESERVED", dao.ticket("A", "overflow")!!.serverStatus)
        }

    @Test
    fun everyObservationIdentityAndReceiptGuardPreservesTheFirstStoredRecord() =
        runTest {
            val first = ticket().copy(serverStatus = "CANCELLED", terminalJson = "receipt", observedAtMs = 200)
            dao.observe(first)
            val changed =
                listOf(
                    first.copy(attemptId = "changed"),
                    first.copy(requestId = "changed"),
                    first.copy(termsJson = "changed"),
                    first.copy(snapshotSha256 = "changed"),
                    first.copy(serverStatus = "SETTLED"),
                    first.copy(terminalJson = "changed"),
                )
            for (incoming in changed) {
                assertTrue(runCatching { dao.observe(incoming) }.exceptionOrNull() is ArenaJournalConflict)
                assertEquals(first, dao.ticket(first.ownerUid, first.ticketId))
            }
        }

    @Test
    fun everyAcknowledgementBindingFailureRollsBackTheEarlierObservation() =
        runTest {
            for (index in 0..2) {
                val row = ticket("ack-$index")
                val original = cancellation(row).copy(commandId = "command-$index")
                val command =
                    when (index) {
                        0 -> original.copy(ticketId = "foreign-ticket")
                        1 -> original.copy(attemptId = "foreign-attempt")
                        else -> original.copy(commandKind = "PREPARE") // command ID differs from issue request ID.
                    }
                dao.insertCommand(command)
                assertTrue(
                    runCatching {
                        dao.acknowledgeObservation(row, command.commandKind, command.commandId)
                    }.exceptionOrNull() is ArenaJournalConflict,
                )
                assertNull(dao.ticket(row.ownerUid, row.ticketId))
                assertEquals(command, dao.command(command.ownerUid, command.commandKind, command.commandId))
            }
        }

    @Test
    fun everySettlementCommandAndDeliveryGuardRejectsBeforeAnyFinancialIntentWrite() =
        runTest {
            dao.observe(ticket())
            claim("A", "ticket")
            dao.saveCheckpoint("A", "ticket", ticket().attemptId, 0, "1", checkpointValidator)
            val before = dao.ticket("A", "ticket")
            val cmd = settlement()
            val row = outbox(cmd)
            val variants =
                listOf(
                    cmd.copy(commandKind = "CANCEL") to row, cmd.copy(state = "ATTENTION") to row,
                    cmd.copy(attemptId = "foreign") to row, cmd.copy(commandId = "foreign") to row,
                    cmd to row.copy(ownerUid = "B"), cmd to row.copy(operation = OutboxOperations.SUBMIT_RATING),
                    cmd to row.copy(payload = "changed"), cmd to row.copy(state = "QUARANTINED"),
                    cmd to row.copy(mutationId = "changed"),
                    cmd to row.copy(entityRef = "lesson_runner:attempt:foreign"),
                    cmd to row.copy(expectedVersion = 1),
                )
            for ((command, delivery) in variants) {
                assertTrue(
                    runCatching {
                        dao.freezeSettlement(command, delivery, checkpointValidator)
                    }.exceptionOrNull() is ArenaJournalConflict,
                )
                assertEquals(before, dao.ticket("A", "ticket"))
                assertNull(dao.ticketCommand("A", "ticket", "SETTLE"))
                assertNull(dao.intent(ticket().attemptId))
                assertNull(dao.queued(row.mutationId))
            }
        }

    @Test
    fun earlySettlementBodyRefusalDoesNotEvaluateTheLaterFallibleMutationKey() =
        runTest {
            val row = ticket().copy(attemptId = "")
            dao.insertTicket(row)
            val cmd = settlement(row)
            val wrongBody =
                outbox().copy(
                    payload = "changed",
                    mutationId = "unused",
                    entityRef = "lesson_runner:attempt:",
                )
            assertTrue(
                runCatching { dao.confirmSettlementReplay(cmd, wrongBody) }.exceptionOrNull() is ArenaJournalConflict,
            )
            assertEquals(row, dao.ticket("A", "ticket"))
            assertNull(dao.queued(wrongBody.mutationId))
        }
}
