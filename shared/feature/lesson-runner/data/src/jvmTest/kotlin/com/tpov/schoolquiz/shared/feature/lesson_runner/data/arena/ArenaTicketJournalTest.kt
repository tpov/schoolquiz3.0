package com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena

import com.tpov.schoolquiz.shared.core.network.NetworkMonitor
import com.tpov.schoolquiz.shared.core.outbox.OutboxSession
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.core.scoring.ChargeClaimMask
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ArenaTicketWireParser
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaCancelResponse
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaCancellationReceipt
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaInitialCheckpoint
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaPrepareRequest
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaPrepareResponse
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketGetRequest
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptCompletionKind
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptIdentity
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.SessionMode
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UnscoredTerminalIntent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Test
import java.security.MessageDigest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ArenaTicketJournalTest {
    private val fixture =
        javaClass.classLoader!!.getResourceAsStream("arena-ticket-client-fixtures.json")!!
            .bufferedReader().use { Json.parseToJsonElement(it.readText()).jsonObject }
    private val request = ArenaPrepareRequest.Selected("player", "client-selected", Difficulty.EASY, "lesson", 12)
    private val codec = ArenaJournalCodec(::sha)
    private val prepared =
        assertIs<ArenaPrepareResponse.Ticket>(
            ArenaTicketWireParser(::sha).prepare(fixture.getValue("issuedSelected").toString(), request),
        )
    private val header get() = prepared.ticket.snapshot.header
    private val storage = JournalStorageFake()
    private val gate = JournalGateFake()
    private val remote = JournalRemoteFake()
    private val checkpoints = JournalCheckpointFake()
    private val initial get() =
        ArenaInitialCheckpoint(
            SessionMode.LEARNING,
            17,
            header.issuedAtMs,
            header.issuedAtMs + 30_000,
        )
    private var online = true
    private var now = 2000L

    private fun TestScope.journal() =
        ArenaTicketJournal(
            storage,
            remote,
            gate,
            object : NetworkMonitor {
                override fun observeOnline() = flowOf(online)

                override suspend fun isOnline() = online
            },
            codec,
            checkpoints,
            { now },
            StandardTestDispatcher(testScheduler),
        )

    private fun intent() =
        UnscoredTerminalIntent(
            AttemptIdentity(
                AttemptId(header.attemptId),
                "player",
                LessonId(header.lessonId),
                header.lessonVersion,
                Difficulty.EASY,
            ),
            AttemptCompletionKind.COMPLETED,
            header.issuedAtMs + 1000,
            emptyList(),
            emptyList(),
            ChargeClaimMask.none(1),
        )

    @Test
    fun consentIsOnlineExplicitAndPersistedBeforeTheFirstRemoteCall() =
        runTest {
            val journal = journal()
            val session = gate.current
            online = false
            assertTrue(journal.recordPrepareConsent(session, request).isFailure)
            assertTrue(storage.commands.isEmpty())
            assertTrue(remote.prepares.isEmpty())
            online = true
            journal.recordPrepareConsent(session, request).getOrThrow()
            remote.prepareAction = { incoming ->
                assertEquals(request, incoming)
                assertEquals(
                    12,
                    assertIs<ArenaPrepareRequest.Selected>(
                        codec.prepare(storage.commands.values.single()),
                    ).maxPriceNolics,
                )
                Result.success(prepared)
            }
            assertEquals(
                ArenaCommandDispatch.RECORDED,
                journal.dispatchPrepare(session, request.requestId).getOrThrow(),
            )
            assertEquals(
                ArenaCommandDispatch.ALREADY_RECORDED,
                journal.dispatchPrepare(session, request.requestId).getOrThrow(),
            )
            assertEquals(1, remote.prepares.size)
        }

    @Test
    fun unknownReplyAndProcessRestartPreserveOriginalIdentityAndCeiling() =
        runTest {
            val first = journal()
            val session = gate.current
            first.recordPrepareConsent(session, request).getOrThrow()
            remote.prepareAction = { Result.failure(IllegalStateException("lost reply")) }
            assertTrue(first.dispatchPrepare(session, request.requestId).isFailure)
            val frozen = storage.commands.values.single().bodyJson
            assertEquals("PENDING", storage.commands.values.single().state)
            now = 9000
            val restarted = journal()
            remote.prepareAction = { Result.success(prepared) }
            restarted.dispatchPrepare(session, request.requestId).getOrThrow()
            assertEquals(frozen, storage.commands.values.single().bodyJson)
            assertEquals(listOf<ArenaPrepareRequest>(request, request), remote.prepares.toList())
        }

    @Test
    fun sameIdCannotChangeConsentAndPriceChangeRequiresAnotherExplicitConsent() =
        runTest {
            val journal = journal()
            val session = gate.current
            journal.recordPrepareConsent(session, request).getOrThrow()
            assertTrue(journal.recordPrepareConsent(session, request.copy(maxPriceNolics = 100)).isFailure)
            remote.prepareAction = { Result.success(ArenaPrepareResponse.PriceChanged) }
            assertEquals(
                ArenaCommandDispatch.PRICE_CHANGED,
                journal.dispatchPrepare(session, request.requestId).getOrThrow(),
            )
            assertEquals("ATTENTION", storage.commands.values.single().state)
            assertTrue(journal.dispatchPrepare(session, request.requestId).isFailure)
            assertTrue(storage.tickets.isEmpty())
        }

    @Test
    fun accountAtoBtoARejectsLateReplyWithoutWritingOrAcknowledgingIt() =
        runTest {
            val journal = journal()
            val session = gate.current
            journal.recordPrepareConsent(session, request).getOrThrow()
            remote.prepareAction = {
                gate.switch("other")
                gate.switch("player")
                Result.success(prepared)
            }
            assertTrue(journal.dispatchPrepare(session, request.requestId).isFailure)
            assertTrue(storage.tickets.isEmpty())
            assertEquals("PENDING", storage.commands.values.single().state)
            assertTrue(journal.read(session, header.ticketId).isFailure)
            assertTrue(journal.recordPrepareConsent(OutboxSession("other", gate.current.generation), request).isFailure)
        }

    @Test
    fun cancellationOfAwaitDoesNotAnnounceServerCancellationOrConsumePendingIntent() =
        runTest {
            val journal = journal()
            val session = gate.current
            journal.recordPrepareConsent(session, request).getOrThrow()
            remote.prepareAction = { throw CancellationException("screen stopped") }
            assertFailsWith<CancellationException> { journal.dispatchPrepare(session, request.requestId) }
            assertEquals("PENDING", storage.commands.values.single().state)
            assertTrue(storage.tickets.isEmpty())
        }

    @Test
    fun persistenceFailureAfterServerReplyKeepsPendingAndNeverReportsRecorded() =
        runTest {
            val journal = journal()
            val session = gate.current
            journal.recordPrepareConsent(session, request).getOrThrow()
            remote.prepareAction = { Result.success(prepared) }
            storage.failAcknowledgment = true
            assertTrue(journal.dispatchPrepare(session, request.requestId).isFailure)
            assertEquals("PENDING", storage.commands.values.single().state)
            assertTrue(storage.tickets.isEmpty())
        }

    @Test
    fun recoveryWithoutOriginalConsentNeverReconstructsPurchaseCeiling() =
        runTest {
            val journal = journal()
            val session = gate.current
            remote.getAction = { Result.success(prepared.ticket) }
            assertIs<ArenaCachedTicket.Available>(journal.recoverTicket(session, header.ticketId).getOrThrow())
            assertTrue(journal.dispatchPrepare(session, request.requestId).isFailure)
            assertTrue(remote.prepares.isEmpty())
            assertTrue(storage.commands.isEmpty())
            val row = storage.ticket("player", header.ticketId)!!
            assertFalse("status" in Json.parseToJsonElement(row.termsJson).jsonObject)
            assertFalse("maxPriceNolics" in row.termsJson)
        }

    @Test
    fun invalidSnapshotIsStillCancellableAndOnlyConfirmedReplyIsTerminal() =
        runTest {
            val journal = journal()
            val session = gate.current
            storage.observe(codec.record(header, now).copy(snapshotJson = "corrupt", snapshotSha256 = "wrong"))
            assertIs<ArenaCachedTicket.SnapshotUnavailable>(journal.read(session, header.ticketId).getOrThrow())
            assertIs<ArenaLaunchClaim.NotFresh>(journal.claimForLaunch(session, header.ticketId, initial).getOrThrow())
            journal.requestCancel(session, header.ticketId, "cancel-original").getOrThrow()
            remote.cancelAction = {
                Result.success(
                    ArenaCancelResponse.RetryRegeneration(header.ticketId, it.requestId),
                )
            }
            assertEquals(
                ArenaCommandDispatch.RETRY_REGEN,
                journal.dispatchCancel(session, header.ticketId).getOrThrow(),
            )
            assertEquals("RESERVED", storage.ticket("player", header.ticketId)!!.serverStatus)
            remote.cancelAction = {
                Result.success(
                    ArenaCancelResponse.Cancelled(
                        header.ticketId,
                        ArenaCancellationReceipt(
                            "arena-cancel:${header.ticketId}",
                            header.issuedAtMs,
                            header.priceNolics,
                            header.lifeCost,
                        ),
                    ),
                )
            }
            journal.dispatchCancel(session, header.ticketId).getOrThrow()
            assertIs<ArenaCachedTicket.Terminal>(journal.read(session, header.ticketId).getOrThrow())
            assertEquals(listOf("cancel-original", "cancel-original"), remote.cancellations.map { it.requestId })
        }

    @Test
    fun launchedSnapshotAndOriginalAttemptSurviveRestartWithoutAnyLiveHierarchyDependency() =
        runTest {
            val journal = journal()
            val session = gate.current
            storage.observe(codec.record(prepared.ticket, now))
            val claim =
                assertIs<ArenaLaunchClaim.Claimed>(
                    journal.claimForLaunch(session, header.ticketId, initial).getOrThrow(),
                )
            assertEquals(header.attemptId, claim.snapshot.header.attemptId)
            assertFalse(journal.evictInactiveSnapshot(session, header.ticketId).getOrThrow())
            val restarted = journal()
            assertIs<ArenaCachedTicket.ResumeOrReconcile>(restarted.read(session, header.ticketId).getOrThrow())
            assertIs<ArenaLaunchClaim.NotFresh>(
                restarted.claimForLaunch(session, header.ticketId, initial).getOrThrow(),
            )
            assertEquals("0", restarted.checkpoint(session, header.ticketId).getOrThrow().json)
            assertTrue(restarted.freezeCompletion(session, header.ticketId, intent()).isFailure)
            assertTrue(restarted.updateCheckpoint(session, header.ticketId, header.attemptId, 0, "1").getOrThrow())
            restarted.freezeCompletion(session, header.ticketId, intent()).getOrThrow()
            val frozen = storage.outbox.values.single()
            val body = Json.parseToJsonElement(frozen.payload).jsonObject
            assertEquals(JsonPrimitive(header.attemptId), body.getValue("attemptId"))
            assertEquals(JsonPrimitive(header.lessonVersion), body.getValue("lessonVersion"))
            assertFalse("percentScore" in body)
            assertFalse("codeAnswer" in body)
            storage.outbox.clear()
            now = 99999
            restarted.restorePendingSettlement(session, header.ticketId).getOrThrow()
            assertEquals(frozen, storage.outbox.values.single())
            assertTrue(restarted.requestCancel(session, header.ticketId, "abort-after-complete").isFailure)
        }

    @Test
    fun failedTypedCheckpointValidationLeavesTicketAvailableAndNoPartialLaunch() =
        runTest {
            val journal = journal()
            val session = gate.current
            storage.observe(codec.record(prepared.ticket, now))
            checkpoints.reject = true
            assertTrue(journal.claimForLaunch(session, header.ticketId, initial).isFailure)
            assertIs<ArenaCachedTicket.Available>(journal.read(session, header.ticketId).getOrThrow())
            assertEquals(null, storage.ticket("player", header.ticketId)!!.checkpointJson)
            checkpoints.reject = false
            assertIs<ArenaLaunchClaim.Claimed>(journal.claimForLaunch(session, header.ticketId, initial).getOrThrow())
        }

    @Test
    fun terminalEvictionStillAllowsOnlyTheExactAlreadyFrozenCompletionWithoutWritingOrReenqueueing() =
        runTest {
            val journal = journal()
            val session = gate.current
            storage.observe(codec.record(prepared.ticket, now))
            journal.claimForLaunch(session, header.ticketId, initial).getOrThrow()
            journal.updateCheckpoint(session, header.ticketId, header.attemptId, 0, "1").getOrThrow()
            val completed = intent()
            journal.freezeCompletion(session, header.ticketId, completed).getOrThrow()
            remote.getAction = {
                Result.success(
                    ArenaTicketWireParser(::sha).get(
                        fixture.getValue("recoveredCancelled").toString(),
                        ArenaTicketGetRequest(session.ownerUid, header.ticketId),
                    ),
                )
            }
            assertIs<ArenaCachedTicket.Terminal>(journal.recoverTicket(session, header.ticketId).getOrThrow())
            assertTrue(journal.evictInactiveSnapshot(session, header.ticketId).getOrThrow())
            val ticketsBefore = storage.tickets.toMap()
            val commandsBefore = storage.commands.toMap()
            val queueBefore = storage.outbox.toMap()
            assertEquals(null, ticketsBefore.values.single().snapshotJson)
            assertEquals(null, ticketsBefore.values.single().checkpointJson)
            checkpoints.reject = true // A replay does not require or substitute a missing checkpoint/snapshot.
            val restarted = journal()
            restarted.freezeCompletion(session, header.ticketId, completed).getOrThrow()
            assertTrue(
                restarted.freezeCompletion(
                    session,
                    header.ticketId,
                    completed.copy(endedAtMs = completed.endedAtMs + 1),
                ).isFailure,
            )
            assertTrue(
                restarted.freezeCompletion(
                    session,
                    header.ticketId,
                    completed.copy(claims = ChargeClaimMask("S")),
                ).isFailure,
            )
            assertTrue(
                restarted.freezeCompletion(
                    session,
                    header.ticketId,
                    completed.copy(identity = completed.identity.copy(userId = "other")),
                ).isFailure,
            )
            assertEquals(ticketsBefore, storage.tickets)
            assertEquals(commandsBefore, storage.commands)
            assertEquals(queueBefore, storage.outbox)
            storage.outbox.clear()
            restarted.freezeCompletion(session, header.ticketId, completed).getOrThrow()
            assertTrue(storage.outbox.isEmpty())
            assertEquals(commandsBefore, storage.commands)
        }

    @Test
    fun checkpointRevisionAndOriginalOwnerLeasePreventStaleProgressOverwrite() =
        runTest {
            val journal = journal()
            val session = gate.current
            storage.observe(codec.record(prepared.ticket, now))
            journal.claimForLaunch(session, header.ticketId, initial).getOrThrow()
            assertFalse(journal.updateCheckpoint(session, header.ticketId, "other-attempt", 0, "1").getOrThrow())
            assertTrue(journal.updateCheckpoint(session, header.ticketId, header.attemptId, 0, "1").getOrThrow())
            assertFalse(journal.updateCheckpoint(session, header.ticketId, header.attemptId, 0, "0").getOrThrow())
            assertTrue(journal.updateCheckpoint(session, header.ticketId, header.attemptId, 1, "0").isFailure)
            gate.switch("other")
            gate.switch("player")
            assertTrue(journal.checkpoint(session, header.ticketId).isFailure)
            assertTrue(journal.updateCheckpoint(session, header.ticketId, header.attemptId, 1, "1").isFailure)
            val restored = journal().checkpoint(gate.current, header.ticketId).getOrThrow()
            assertEquals("1", restored.json)
            assertEquals(1L, restored.revision)
            assertEquals(header.attemptId, restored.snapshot.header.attemptId)
        }

    @Test
    fun responseWaitingForSharedCommitGateCannotBeatCompletedAccountSwitch() =
        runTest {
            val journal = journal()
            val session = gate.current
            journal.recordPrepareConsent(session, request).getOrThrow()
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            remote.prepareAction = {
                entered.complete(Unit)
                release.await()
                Result.success(prepared)
            }
            val delivery = async { journal.dispatchPrepare(session, request.requestId) }
            entered.await()
            gate.switch("other")
            release.complete(Unit)
            assertTrue(delivery.await().isFailure)
            assertTrue(storage.tickets.isEmpty())
            assertNotNull(storage.command("player", "PREPARE", request.requestId))
        }

    @Test
    fun abortNeverBecomesUnscoredSubmitAndTerminalRecoveryNeverLaunches() =
        runTest {
            val journal = journal()
            val session = gate.current
            storage.observe(codec.record(prepared.ticket, now))
            journal.claimForLaunch(session, header.ticketId, initial).getOrThrow()
            assertTrue(
                journal.freezeCompletion(
                    session,
                    header.ticketId,
                    intent().copy(completionKind = AttemptCompletionKind.ABORTED),
                ).isFailure,
            )
            assertTrue(storage.outbox.isEmpty())
            journal.requestCancel(session, header.ticketId, "abort").getOrThrow()
            assertTrue(storage.outbox.isEmpty())
        }

    private fun sha(value: String): String =
        MessageDigest.getInstance(
            "SHA-256",
        ).digest(value.toByteArray()).joinToString("") {
            "%02x".format(it)
        }
}
