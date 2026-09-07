package com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena

import com.tpov.schoolquiz.shared.core.network.NetworkMonitor
import com.tpov.schoolquiz.shared.core.persistence.ArenaTicketRecordEntity
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ArenaTicketWireParser
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaCancelResponse
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaPrepareRequest
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaPrepareResponse
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRemoteFailure
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRemoteFailureKind
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketPage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Test
import java.security.MessageDigest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Boundary regressions reuse the journal's canonical fakes; no SDK, Room or live account is opened. */
class ArenaJournalBoundaryTest {
    private val request = ArenaPrepareRequest.Selected("player", "client-selected", Difficulty.EASY, "lesson", 12)
    private val codec = ArenaJournalCodec(::sha)
    private val prepared =
        javaClass.classLoader!!.getResourceAsStream("arena-ticket-client-fixtures.json")!!
            .bufferedReader().use {
                val fixture = Json.parseToJsonElement(it.readText()).jsonObject
                assertIs<ArenaPrepareResponse.Ticket>(
                    ArenaTicketWireParser(::sha).prepare(fixture.getValue("issuedSelected").toString(), request),
                )
            }
    private val header get() = prepared.ticket.snapshot.header
    private val storage = JournalStorageFake()
    private val gate = JournalGateFake()
    private val remote = JournalRemoteFake()
    private val now = 2_000L

    @Test
    fun cancellingASuspendedExchangePreservesTheExactPendingConsentWithoutFailureWrite() =
        runTest {
            val journal = journal()
            journal.recordPrepareConsent(gate.current, request).getOrThrow()
            val before = storage.commands.toMap()
            val entered = CompletableDeferred<Unit>()
            remote.prepareAction = {
                entered.complete(Unit)
                awaitCancellation()
            }
            val job = launch { journal.dispatchPrepare(gate.current, request.requestId) }
            entered.await()
            job.cancelAndJoin()
            assertTrue(job.isCancelled)
            assertEquals(before, storage.commands)
            assertTrue(storage.tickets.isEmpty())
            assertTrue(storage.outbox.isEmpty())
            assertEquals(listOf<ArenaPrepareRequest>(request), remote.prepares.toList())
        }

    @Test
    fun cancellationReturnedByCancelTransportEscapesAndDoesNotConfirmOrRecordFailure() =
        runTest {
            val journal = journal()
            storage.observe(codec.record(prepared.ticket, now))
            journal.requestCancel(gate.current, header.ticketId, "explicit-cancel").getOrThrow()
            val commands = storage.commands.toMap()
            val tickets = storage.tickets.toMap()
            val cancellation = CancellationException("transport cancelled")
            remote.cancelAction = { Result.failure(cancellation) }
            assertOriginalCancellation(
                cancellation,
                assertFailsWith<CancellationException> { journal.dispatchCancel(gate.current, header.ticketId) },
            )
            assertEquals(commands, storage.commands)
            assertEquals(tickets, storage.tickets)
            assertTrue(storage.outbox.isEmpty())
        }

    @Test
    fun cancellationReturnedByGetIsNotAResultFailureOrARecoveryObservation() =
        runTest {
            val journal = journal()
            storage.observe(codec.record(header, now))
            val before = storage.tickets.toMap()
            val cancellation = CancellationException("get cancelled")
            remote.getAction = { Result.failure(cancellation) }
            assertOriginalCancellation(
                cancellation,
                assertFailsWith<CancellationException> { journal.recoverTicket(gate.current, header.ticketId) },
            )
            assertEquals(before, storage.tickets)
            assertTrue(storage.commands.isEmpty())
            assertTrue(storage.outbox.isEmpty())
        }

    @Test
    fun cancellationThrownByListEscapesWithoutPublishingAPartialPage() =
        runTest {
            val journal = journal()
            val cancellation = CancellationException("list cancelled")
            remote.listAction = { throw cancellation }
            assertOriginalCancellation(
                cancellation,
                assertFailsWith<CancellationException> { journal.recoverPage(gate.current) },
            )
            assertTrue(storage.tickets.isEmpty())
            assertTrue(storage.commands.isEmpty())
            assertTrue(storage.outbox.isEmpty())
        }

    @Test
    fun localStorageCancellationEscapesThePublicResultBoundaryWithoutCallingTransport() =
        runTest {
            val cancellation = CancellationException("local read cancelled")
            val cancelOnRead =
                object : ArenaJournalStorage by storage {
                    override suspend fun ticket(
                        owner: String,
                        ticket: String,
                    ): ArenaTicketRecordEntity? = throw cancellation
                }
            val journal = journal(cancelOnRead)
            assertOriginalCancellation(
                cancellation,
                assertFailsWith<CancellationException> { journal.read(gate.current, header.ticketId) },
            )
            assertTrue(storage.tickets.isEmpty())
            assertTrue(storage.commands.isEmpty())
            assertTrue(storage.outbox.isEmpty())
            assertTrue(remote.prepares.isEmpty())
            assertTrue(remote.cancellations.isEmpty())
        }

    @Test
    fun stateKeepsTerminalThenDecisionPriorityAndNeverRelaunchesAnUnsafeOrLaunchedSnapshot() =
        runTest {
            val journal = journal()
            val valid = codec.record(prepared.ticket, now)
            val unavailable = valid.copy(snapshotJson = "corrupt", snapshotSha256 = "wrong")

            suspend fun read(row: ArenaTicketRecordEntity): ArenaCachedTicket {
                storage.tickets[row.ownerUid to row.ticketId] = row
                return journal.read(gate.current, row.ticketId).getOrThrow()
            }
            assertIs<ArenaCachedTicket.Terminal>(
                read(unavailable.copy(serverStatus = "CANCELLED", decision = "CANCEL")),
            )
            assertIs<ArenaCachedTicket.DecisionPending>(read(unavailable.copy(decision = "CANCEL")))
            assertIs<ArenaCachedTicket.SnapshotUnavailable>(read(unavailable))
            assertIs<ArenaCachedTicket.SnapshotUnavailable>(read(unavailable.copy(localUse = "LAUNCHED")))
            assertEquals(
                valid.let { codec.decode(it).snapshot },
                assertIs<ArenaCachedTicket.Available>(read(valid)).snapshot,
            )
            assertEquals(
                codec.decode(valid).snapshot,
                assertIs<ArenaCachedTicket.ResumeOrReconcile>(read(valid.copy(localUse = "LAUNCHED"))).snapshot,
            )
            assertTrue(storage.commands.isEmpty())
            assertTrue(storage.outbox.isEmpty())
        }

    @Test
    fun originalConsentEpochReachesRemoteEntryAndFreshRetryKeepsExactRetainedBody() =
        runTest {
            val journal = journal()
            val original = gate.current
            journal.recordPrepareConsent(original, request).getOrThrow()
            val before = storage.commands.toMap()
            remote.beforeBoundCall = { expected ->
                gate.switch("other")
                gate.switch("player")
                gate.withOwner(expected) { Unit }
            }
            val failure = journal.dispatchPrepare(original, request.requestId).exceptionOrNull()
            assertEquals(ArenaRemoteFailureKind.ACCOUNT_CHANGED, assertIs<ArenaRemoteFailure>(failure).kind)
            assertEquals(listOf(original), remote.boundSessions)
            assertTrue(remote.prepares.isEmpty())
            assertEquals(before, storage.commands)
            assertTrue(storage.tickets.isEmpty() && storage.outbox.isEmpty())
            remote.beforeBoundCall = { expected -> gate.withOwner(expected) { Unit } }
            remote.prepareAction = { Result.success(prepared) }
            journal.dispatchPrepare(gate.current, request.requestId).getOrThrow()
            val retained = storage.commands.values.single()
            assertEquals(before.values.single().bodyJson, retained.bodyJson)
            assertEquals(before.values.single().bodySha256, retained.bodySha256)
            assertEquals(listOf<ArenaPrepareRequest>(request), remote.prepares)
            assertEquals(listOf(original, gate.current), remote.boundSessions)
        }

    @Test
    fun listGetAndCancelAlsoUseTheOriginalSessionWithoutAnyPlainRemoteFallback() =
        runTest {
            val journal = journal()
            val original = gate.current
            remote.beforeBoundCall = { expected -> gate.withOwner(expected) { Unit } }
            remote.listAction = { Result.success(ArenaTicketPage(listOf(header), null)) }
            remote.getAction = { Result.success(prepared.ticket) }
            journal.recoverPage(original).getOrThrow()
            journal.recoverTicket(original, header.ticketId).getOrThrow()
            journal.requestCancel(original, header.ticketId, "same-cancel").getOrThrow()
            remote.cancelAction = {
                Result.success(
                    ArenaCancelResponse.RetryRegeneration(header.ticketId, it.requestId),
                )
            }
            assertEquals(
                ArenaCommandDispatch.RETRY_REGEN,
                journal.dispatchCancel(original, header.ticketId).getOrThrow(),
            )
            assertEquals(List(3) { original }, remote.boundSessions)
            assertEquals("same-cancel", remote.cancellations.single().requestId)
            assertTrue(storage.outbox.isEmpty())
        }

    private fun TestScope.journal(port: ArenaJournalStorage = storage) =
        ArenaTicketJournal(
            port,
            remote,
            gate,
            object : NetworkMonitor {
                override fun observeOnline() = flowOf(true)

                override suspend fun isOnline() = true
            },
            codec,
            JournalCheckpointFake(),
            { now },
            StandardTestDispatcher(testScheduler),
        )

    private fun assertOriginalCancellation(
        expected: CancellationException,
        actual: CancellationException,
    ) {
        assertEquals(expected::class, actual::class)
        assertEquals(expected.message, actual.message)
        // withContext may recover the stacktrace with a copy whose direct cause is the original.
        assertTrue(actual === expected || actual.cause === expected)
    }

    private fun sha(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
