@file:Suppress("MagicNumber")
@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena

import com.tpov.schoolquiz.shared.core.network.NetworkMonitor
import com.tpov.schoolquiz.shared.core.outbox.OutboxSession
import com.tpov.schoolquiz.shared.core.persistence.ArenaTicketRecordEntity
import com.tpov.schoolquiz.shared.core.persistence.LessonAttemptIntentEntity
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.core.question_schema.OptionId
import com.tpov.schoolquiz.shared.core.question_schema.QuestionContent
import com.tpov.schoolquiz.shared.core.question_schema.RedactedQuestionContent
import com.tpov.schoolquiz.shared.core.scoring.UserAnswer
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ArenaTicketWireParser
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.arenaCheckpointCompletion
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.arenaCheckpointFromReady
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.arenaCheckpointToReady
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computeTimer
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.submitAnswer
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaEntryTicket
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaInitialCheckpoint
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaPendingFeedback
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaPrepareRequest
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaPrepareResponse
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRemoteFailure
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRemoteFailureKind
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRunnerCheckpoint
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRunnerCheckpointBinding
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRunnerPhase
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketGetRequest
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.SessionMode
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.TimerCoefficients
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaRunDecision
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaRunRecoveryReason
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionStamp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import java.security.MessageDigest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArenaJournalRunViewTest {
    @Test
    fun absentIsOneReadAndNeverRecoveryOrAFreshAttempt() =
        runTest {
            val f = RunReadFixture(this)
            f.storage.tickets.clear()
            assertEquals(ArenaRunReadView.LocalAbsent, f.read())
            f.assertReadOnly(1)
        }

    @Test
    fun preparedIsValidatedButHeaderOnlyAndNeverClaims() =
        runTest {
            val f = RunReadFixture(this)
            val before = f.financialState()
            assertEquals(f.header, assertIs<ArenaRunReadView.Prepared>(f.read()).header)
            assertEquals(before, f.financialState())
            assertEquals("UNUSED", f.row.localUse)
            f.assertReadOnly(1)
        }

    @Test
    fun allFourDurablePhasesReturnExactSavedBytesAndRevision() =
        runTest {
            for (phase in ArenaRunnerPhase.entries) {
                val f = RunReadFixture(this)
                f.launch(phase)
                val before = f.financialState()
                val saved = assertIs<ArenaRunReadView.Saved>(f.read()).checkpoint
                assertEquals(f.row.checkpointJson, saved.json)
                assertEquals(f.row.snapshotSha256, saved.snapshotSha256)
                assertEquals(7L, saved.revision)
                assertEquals(f.header, saved.snapshot.header)
                assertEquals(f.ticket.snapshot.questions, saved.snapshot.questions)
                assertEquals(phase, f.checkpoints.decode(saved.json, f.binding).phase)
                assertEquals(before, f.financialState())
                f.assertReadOnly(1)
            }
        }

    @Test
    fun terminalAndPendingNeverDecodeCorruptOrAbsentBankCheckpointOrReceipt() =
        runTest {
            for (status in listOf("RESERVED", "CANCELLED", "SETTLED")) {
                for (decision in listOf("CANCEL", "SETTLE")) {
                    val f = RunReadFixture(this)
                    f.row =
                        f.row.copy(
                            serverStatus = status,
                            decision = decision,
                            localUse = "LAUNCHED",
                            snapshotJson = "unsafe bank",
                            snapshotSha256 = "bad hash",
                            checkpointJson = "unsafe checkpoint",
                            checkpointRevision = 8,
                            terminalJson = if (status == "RESERVED") null else "opaque receipt",
                        )
                    val before = f.financialState()
                    val result = f.read()
                    if (status == "RESERVED") {
                        val pending = assertIs<ArenaRunReadView.DecisionPending>(result)
                        assertEquals(ArenaRunDecision.valueOf(decision), pending.decision)
                        assertEquals(f.header.ticketId, pending.header.ticketId)
                    } else {
                        assertEquals(status, assertIs<ArenaRunReadView.Terminal>(result).header.status.name)
                    }
                    assertEquals(before, f.financialState())
                    f.assertReadOnly(1)
                }
            }
        }

    @Test
    fun terminalHeaderOnlyObservationDoesNotRequireAReceiptOrInventItsAccounting() =
        runTest {
            val f = RunReadFixture(this)
            f.row = f.journalCodec.record(f.header, 1_000).copy(serverStatus = "SETTLED")
            val view = assertIs<ArenaRunReadView.Terminal>(f.read())
            assertEquals("SETTLED", view.header.status.name)
            assertNull(f.row.terminalJson)
            f.assertReadOnly(1)
        }

    @Test
    fun unknownDuplicateOrReorderedTermsNeverPublishPreparedOrSavedContinuation() =
        runTest {
            for (launched in listOf(false, true)) {
                for (change in listOf("unknown", "duplicate", "reordered")) {
                    val f = RunReadFixture(this)
                    if (launched) f.launch()
                    val original = f.row.termsJson
                    val fields = Json.parseToJsonElement(original).jsonObject
                    val altered =
                        when (change) {
                            "unknown" -> JsonObject(fields + ("futureTerms" to JsonPrimitive("private"))).toString()
                            "duplicate" -> original.dropLast(1) + ",\"schemaVersion\":1}"
                            else -> JsonObject(fields.entries.reversed().associate { it.key to it.value }).toString()
                        }
                    assertTrue(altered != original)
                    f.row = f.row.copy(termsJson = altered)
                    val before = f.financialState()
                    val result = assertIs<ArenaRunReadView.NeedsRecovery>(f.read())
                    assertEquals(ArenaRunRecoveryReason.INVALID_RECORD, result.reason)
                    assertNull(result.header)
                    assertEquals(before, f.financialState())
                    f.assertReadOnly(1)
                }
            }
        }

    @Test
    fun reservedWithAnyPersistedTerminalCannotPublishPlayableOrPendingState() =
        runTest {
            for ((localUse, decision) in listOf(
                "UNUSED" to "NONE",
                "LAUNCHED" to "NONE",
                "LAUNCHED" to "CANCEL",
                "LAUNCHED" to "SETTLE",
            )) {
                for (terminal in listOf("null", "{}", "unsafe receipt")) {
                    val f = RunReadFixture(this)
                    if (localUse == "LAUNCHED") f.launch()
                    f.row = f.row.copy(decision = decision, terminalJson = terminal)
                    val before = f.financialState()
                    val result = assertIs<ArenaRunReadView.NeedsRecovery>(f.read())
                    assertEquals(ArenaRunRecoveryReason.INVALID_RECORD, result.reason)
                    assertNull(result.header)
                    assertEquals(before, f.financialState())
                    f.assertReadOnly(1)
                }
            }
        }

    @Test
    fun unknownClosedFlagsAndInvalidRevisionOrObservationTimeNeverBecomePrepared() =
        runTest {
            val changes: List<(ArenaTicketRecordEntity) -> ArenaTicketRecordEntity> =
                listOf(
                    { it.copy(serverStatus = "ISSUED") },
                    { it.copy(localUse = "UNKNOWN") },
                    { it.copy(decision = "ACK") },
                    { it.copy(checkpointRevision = -1) },
                    { it.copy(observedAtMs = -1) },
                    { it.copy(checkpointJson = "0") },
                    { it.copy(checkpointRevision = 1) },
                    { it.copy(decision = "SETTLE") },
                )
            for (change in changes) {
                val f = RunReadFixture(this)
                f.row = change(f.row)
                val result = assertIs<ArenaRunReadView.NeedsRecovery>(f.read())
                assertEquals(ArenaRunRecoveryReason.INVALID_RECORD, result.reason)
                assertNull(result.header)
                f.assertReadOnly(1)
            }
        }

    @Test
    fun foreignOwnerTicketAttemptRequestAndCorruptHeaderAreNotExposed() =
        runTest {
            val changes: List<(ArenaTicketRecordEntity) -> ArenaTicketRecordEntity> =
                listOf(
                    { it.copy(ownerUid = "other") },
                    { it.copy(ticketId = "b".repeat(64)) },
                    { it.copy(attemptId = "other") },
                    { it.copy(requestId = "other") },
                    { it.copy(termsJson = "corrupt secret") },
                )
            for (change in changes) {
                val f = RunReadFixture(this)
                f.readTransform = change
                val result = assertIs<ArenaRunReadView.NeedsRecovery>(f.read())
                assertEquals(ArenaRunRecoveryReason.INVALID_RECORD, result.reason)
                assertNull(result.header)
                f.assertReadOnly(1)
            }
        }

    @Test
    fun activeMissingOrUnsafeBankStaysRecoveryWithoutResettingLaunched() =
        runTest {
            for (bank in listOf(null, "unsafe bank")) {
                val f = RunReadFixture(this)
                f.launch()
                f.row = f.row.copy(snapshotJson = bank)
                val before = f.financialState()
                val result = assertIs<ArenaRunReadView.NeedsRecovery>(f.read())
                assertEquals(ArenaRunRecoveryReason.SNAPSHOT_UNAVAILABLE, result.reason)
                assertEquals(f.header, result.header)
                assertEquals(before, f.financialState())
            }
        }

    @Test
    fun legacyLaunchedWithoutCheckpointNeedsRecoveryNeverFresh() =
        runTest {
            val f = RunReadFixture(this)
            f.row = f.row.copy(localUse = "LAUNCHED")
            val result = assertIs<ArenaRunReadView.NeedsRecovery>(f.read())
            assertEquals(ArenaRunRecoveryReason.CHECKPOINT_UNAVAILABLE, result.reason)
            assertEquals("LAUNCHED", f.row.localUse)
        }

    @Test
    fun malformedForeignAndUnknownFieldCheckpointsUseRequiredStrictCodec() =
        runTest {
            for (kind in listOf("syntax", "owner", "field")) {
                val f = RunReadFixture(this)
                f.launch()
                val json = requireNotNull(f.row.checkpointJson)
                f.row =
                    f.row.copy(
                        checkpointJson =
                            when (kind) {
                                "syntax" -> "0"
                                "owner" -> json.replace("\"ownerUid\":\"player\"", "\"ownerUid\":\"other\"")
                                else -> json.dropLast(1) + ",\"score\":100}"
                            },
                    )
                assertEquals(
                    ArenaRunRecoveryReason.CHECKPOINT_INVALID,
                    assertIs<ArenaRunReadView.NeedsRecovery>(f.read()).reason,
                )
                f.assertReadOnly(1)
            }
        }

    @Test
    fun rowChangedAfterReadDoesNotMixOldStateWithNewCheckpoint() =
        runTest {
            val f = RunReadFixture(this)
            f.launch()
            val original = f.row
            f.afterRead = { f.row = f.row.copy(decision = "CANCEL", checkpointJson = "bad later checkpoint") }
            val first = assertIs<ArenaRunReadView.Saved>(f.read())
            assertEquals(original.checkpointJson, first.checkpoint.json)
            assertIs<ArenaRunReadView.DecisionPending>(f.read())
            f.assertReadOnly(2)
        }

    @Test
    fun cancelledOrSettledBeforeReadNeverPublishesOldPlayableCheckpoint() =
        runTest {
            for (status in listOf("CANCELLED", "SETTLED")) {
                val f = RunReadFixture(this)
                f.launch()
                f.row = f.row.copy(serverStatus = status)
                assertIs<ArenaRunReadView.Terminal>(f.read())
                f.assertReadOnly(1)
            }
        }

    @Test
    fun oldEpochBeforeDispatchIsRejectedBeforeAnyRowRead() =
        runTest {
            val f = RunReadFixture(this)
            val work = async { f.journal.readRunView(f.original, f.header.ticketId) }
            f.gate.current = OutboxSession("player", 3)
            runCurrent()
            assertEquals(
                ArenaRemoteFailureKind.ACCOUNT_CHANGED,
                assertIs<ArenaRemoteFailure>(work.await().exceptionOrNull()).kind,
            )
            assertEquals(0, f.reads)
        }

    @Test
    fun postReadAtoBtoACannotEscapeTheSameOriginalGate() =
        runTest {
            val f = RunReadFixture(this)
            f.afterRead = { f.gate.current = OutboxSession("player", 3) }
            val result = f.journal.readRunView(f.original, f.header.ticketId)
            assertEquals(
                ArenaRemoteFailureKind.ACCOUNT_CHANGED,
                assertIs<ArenaRemoteFailure>(result.exceptionOrNull()).kind,
            )
            f.assertReadOnly(1)
        }

    @Test
    fun cancelledSuspendedReadDoesNotTouchFinancialStateOrReturnResultFailure() =
        runTest {
            val f = RunReadFixture(this)
            val entered = CompletableDeferred<Unit>()
            val never = CompletableDeferred<Unit>()
            f.afterRead = {
                entered.complete(Unit)
                never.await()
            }
            val before = f.financialState()
            val work = async { f.read() }
            entered.await()
            work.cancel(CancellationException("stop local read"))
            assertFailsWith<CancellationException> { work.await() }
            assertEquals(before, f.financialState())
            f.assertReadOnly(1)
        }

    @Test
    fun directlyThrownCancellationIsOriginalOrDirectRecoveryCopy() =
        runTest {
            val f = RunReadFixture(this)
            val expected = CancellationException("local read cancelled")
            f.afterRead = { throw expected }
            val actual = assertFailsWith<CancellationException> { f.read() }
            assertRunReadCancellation(expected, actual)
            f.assertReadOnly(1)
        }

    @Test
    fun retainedDecisionCommandOriginalIntentAndExistingQueueAreNeverReadOrMutated() =
        runTest {
            val f = RunReadFixture(this)
            f.launch(ArenaRunnerPhase.COMPLETING)
            val intent = arenaCheckpointCompletion(f.checkpoint(ArenaRunnerPhase.COMPLETING), f.binding)
            val command = f.journalCodec.completion(f.row, intent)
            val queue = f.journalCodec.outbox(command)
            f.storage.commands[Triple(command.ownerUid, command.commandKind, command.commandId)] = command
            f.storage.outbox[queue.mutationId] = queue
            f.storage.firstIntents[f.header.attemptId] =
                LessonAttemptIntentEntity(
                    f.header.attemptId,
                    queue.ownerUid,
                    queue.mutationId,
                    queue.operation,
                    queue.payload,
                    queue.entityRef,
                    queue.expectedVersion,
                )
            f.row = f.row.copy(decision = "SETTLE", snapshotJson = null, checkpointJson = null)
            val before = f.financialState()
            assertIs<ArenaRunReadView.DecisionPending>(f.read())
            assertEquals(before, f.financialState())
            f.assertReadOnly(1)
        }
}

/** Reuses canonical storage/gate/remote by delegation; only observes a single local read. */
internal class RunReadFixture(scope: TestScope, hard: Boolean = false) {
    val storage = JournalStorageFake()
    val gate = JournalGateFake()
    val remote = JournalRemoteFake()
    val journalCodec = ArenaJournalCodec(::runReadSha)
    val checkpoints = ArenaRunnerCheckpointCodec()
    val original = gate.current
    val stamp get() = ArenaSessionStamp(original.ownerUid, original.generation)
    val dispatcher = StandardTestDispatcher(scope.testScheduler)
    var reads = 0
    var gateEntries = 0
    val seenSessions = mutableListOf<OutboxSession>()
    var afterRead: suspend () -> Unit = {}
    var readTransform: (ArenaTicketRecordEntity) -> ArenaTicketRecordEntity = { it }
    val ticket: ArenaEntryTicket.Reserved
    val header get() = ticket.snapshot.header
    val binding get() = ArenaRunnerCheckpointBinding(ticket.snapshot, requireNotNull(row.snapshotSha256))
    var row: ArenaTicketRecordEntity
        get() = storage.tickets.getValue(header.ownerUid to header.ticketId)
        set(value) {
            storage.tickets[header.ownerUid to header.ticketId] = value
        }
    val journal: ArenaTicketJournal

    init {
        val fixtures =
            javaClass.classLoader!!.getResourceAsStream("arena-ticket-client-fixtures.json")!!
                .bufferedReader().use { Json.parseToJsonElement(it.readText()).jsonObject }
        val wire = fixtures.getValue(if (hard) "issuedHard" else "recoveredSelected")
        val id = wire.jsonObject.getValue("ticket").jsonObject.getValue("ticketId").jsonPrimitive.content
        val parser = ArenaTicketWireParser(::runReadSha)
        ticket =
            assertIs<ArenaEntryTicket.Reserved>(
                if (hard) {
                    assertIs<ArenaPrepareResponse.Ticket>(
                        parser.prepare(
                            wire.toString(),
                            ArenaPrepareRequest.Selected("player", "client-hard", Difficulty.HARD, "lesson", 10),
                        ),
                    ).ticket
                } else {
                    parser.get(wire.toString(), ArenaTicketGetRequest("player", id))
                },
            )
        row = journalCodec.record(ticket, 1_000)
        val countedStorage =
            object : ArenaJournalStorage by storage {
                override suspend fun ticket(
                    owner: String,
                    ticket: String,
                ): ArenaTicketRecordEntity? {
                    reads++
                    val immutable = storage.ticket(owner, ticket)?.let(readTransform)
                    afterRead()
                    return immutable
                }

                override suspend fun command(
                    owner: String,
                    kind: String,
                    id: String,
                ): Nothing = error("Run view must not read a command")

                override suspend fun ticketCommand(
                    owner: String,
                    ticket: String,
                    kind: String,
                ): Nothing = error("Run view must not read a ticket command")
            }
        val countedGate =
            object : ArenaOwnerGate by gate {
                override fun capture(owner: String): OutboxSession = error("Must not recapture")

                override suspend fun <T> withOwner(
                    session: OutboxSession,
                    block: suspend () -> T,
                ): T {
                    gateEntries++
                    seenSessions += session
                    return gate.withOwner(session, block)
                }
            }
        journal =
            ArenaTicketJournal(
                countedStorage,
                remote,
                countedGate,
                object : NetworkMonitor {
                    override fun observeOnline() = flowOf(false)

                    override suspend fun isOnline(): Boolean = error("No network check during local read")
                },
                journalCodec,
                checkpoints,
                { error("No clock during local read") },
                dispatcher,
            )
    }

    fun checkpoint(phase: ArenaRunnerPhase): ArenaRunnerCheckpoint {
        val duration =
            computeTimer(
                ticket.snapshot.questions.first().display,
                header.difficulty,
                TimerCoefficients.Default,
            ).seconds
        val json =
            checkpoints.initial(
                ticket.snapshot,
                binding.snapshotSha256,
                ArenaInitialCheckpoint(
                    SessionMode.LEARNING,
                    91,
                    1_000,
                    1_000 + duration * 1_000L,
                ),
            )
        val initial = checkpoints.decode(json, binding)
        val display = ticket.snapshot.questions.single().display
        val option =
            when (display) {
                is QuestionContent.SingleChoice -> display.options.first().id
                is RedactedQuestionContent.SingleChoice -> OptionId(display.options.first().id)
                else -> error("Actual pinned SINGLE fixture expected")
            }
        return when (phase) {
            ArenaRunnerPhase.ANSWERING -> initial
            ArenaRunnerPhase.PAUSED -> initial.copy(phase = phase)
            ArenaRunnerPhase.FEEDBACK ->
                initial.copy(
                    phase = phase,
                    currentDraft = UserAnswerDraft.SingleChoiceDraft(option),
                    pendingFeedback =
                        ArenaPendingFeedback(
                            ticket.snapshot.questions.single().questionId,
                            0,
                            UserAnswerDraft.SingleChoiceDraft(option),
                        ),
                )
            ArenaRunnerPhase.COMPLETING ->
                arenaCheckpointFromReady(
                    submitAnswer(
                        arenaCheckpointToReady(initial, binding),
                        UserAnswer.SingleChoiceAnswer(option),
                        1_100,
                    ),
                    binding,
                    phase,
                    endedAtMs = 1_101,
                )
        }
    }

    fun launch(phase: ArenaRunnerPhase = ArenaRunnerPhase.ANSWERING) {
        row =
            row.copy(
                localUse = "LAUNCHED",
                checkpointJson = checkpoints.encode(checkpoint(phase), binding),
                checkpointRevision = 7,
            )
    }

    suspend fun read(): ArenaRunReadView = journal.readRunView(original, header.ticketId).getOrThrow()

    fun financialState(): List<Any> =
        listOf(
            storage.tickets.toMap(),
            storage.commands.toMap(),
            storage.outbox.toMap(),
            storage.firstIntents.toMap(),
            storage.acknowledgedMutations.toSet(),
            storage.scoredAttempts.toSet(),
        )

    fun assertReadOnly(expectedReads: Int) {
        assertEquals(expectedReads, reads)
        assertEquals(expectedReads, gateEntries)
        assertEquals(List(expectedReads) { original }, seenSessions)
        assertTrue(remote.boundSessions.isEmpty() && remote.prepares.isEmpty() && remote.cancellations.isEmpty())
        assertEquals(0, storage.ticketPageReads)
        assertEquals(0, storage.preparePageReads)
    }
}

internal fun assertRunReadCancellation(
    expected: CancellationException,
    actual: CancellationException,
) {
    assertTrue(actual === expected || actual.cause === expected)
    assertEquals(expected::class, actual::class)
    assertEquals(expected.message, actual.message)
}

private fun runReadSha(value: String): String =
    MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
