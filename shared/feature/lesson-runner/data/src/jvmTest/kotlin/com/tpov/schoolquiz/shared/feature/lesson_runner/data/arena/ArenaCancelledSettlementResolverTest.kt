@file:Suppress("MagicNumber") // Protocol fixture values and deliberate boundary cases.

package com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena

import com.tpov.schoolquiz.shared.core.persistence.ARENA_RESOLVED_CANCELLED
import com.tpov.schoolquiz.shared.core.persistence.ArenaCancellationValidator
import com.tpov.schoolquiz.shared.core.persistence.ArenaCommandEntity
import com.tpov.schoolquiz.shared.core.persistence.ArenaTicketRecordEntity
import com.tpov.schoolquiz.shared.core.persistence.LessonAttemptIntentEntity
import com.tpov.schoolquiz.shared.core.scoring.ChargeClaimMask
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ArenaTicketStatusRemoteDataSource
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ArenaTicketWireParser
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaCancellationReceipt
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRemoteFailure
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRemoteFailureKind
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketGetRequest
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketStatus
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketStatusMetadata
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketStatusObservation
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptCompletionKind
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptIdentity
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UnscoredTerminalIntent
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Test
import java.security.MessageDigest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArenaCancelledSettlementResolverTest {
    @Test
    fun realCancellationResolvesOnlyTheOriginalDeliveryAndPreservesItsBodyAndFailure() =
        runTest {
            val f = CancellationResolutionFixture()
            f.replaceCommand(f.command.copy(state = "ATTENTION", lastFailure = "TICKET_CANCELLED"))
            val other = f.delivery.copy(id = 2, mutationId = "another-mutation", ownerUid = "other")
            f.storage.outbox[other.mutationId] = other
            val result = assertIs<ArenaCancellationResolution.ResolvedCancelled>(f.resolve(this).getOrThrow())
            assertEquals(f.cancelled, result.observation)
            assertEquals("CANCELLED", f.row.serverStatus)
            assertEquals("SETTLE", f.row.decision)
            assertEquals(f.original.snapshotJson, f.row.snapshotJson)
            assertEquals(f.original.checkpointJson, f.row.checkpointJson)
            assertEquals(f.command.copy(state = ARENA_RESOLVED_CANCELLED, lastFailure = "TICKET_CANCELLED"), f.retained)
            assertEquals(f.first, f.storage.firstIntents[f.header.attemptId])
            assertNull(f.storage.outbox[f.delivery.mutationId])
            assertEquals(other, f.storage.outbox[other.mutationId])
            assertTrue(f.storage.acknowledgedMutations.isEmpty())
            assertEquals(listOf(f.request), f.requests)
        }

    @Test
    fun reservedStatusDoesNotAcknowledgeWriteOrEraseAnything() =
        runTest {
            val f = CancellationResolutionFixture()
            f.response = f.status("recoveredSelected")
            val before = f.snapshot()
            assertIs<ArenaCancellationResolution.Reserved>(f.resolve(this).getOrThrow())
            assertEquals(before, f.snapshot())
        }

    @Test
    fun realSettledStatusIsOnlyAnObservationNotAnAcknowledgementOrSyntheticSuccess() =
        runTest {
            val f = CancellationResolutionFixture("issuedHard")
            f.response = f.status("recoveredSettled")
            val before = f.snapshot()
            val result = assertIs<ArenaCancellationResolution.SettledUnchanged>(f.resolve(this).getOrThrow())
            assertEquals(75.5, result.observation.receipt.percentScore)
            assertEquals(before, f.snapshot())
        }

    @Test
    fun networkWaitDoesNotHoldTheSharedOwnerGate() =
        runTest {
            val f = CancellationResolutionFixture()
            var entered = false
            f.action = {
                f.gate.withOwner(f.gate.current) { entered = true }
                Result.success(f.cancelled)
            }
            withTimeout(1000) { f.resolve(this@runTest).getOrThrow() }
            assertTrue(entered)
        }

    @Test
    fun captureHappensBeforeTheFirstDispatcherSuspension() =
        runTest {
            val f = CancellationResolutionFixture()
            val before = f.snapshot()
            val call = async(start = CoroutineStart.UNDISPATCHED) { f.resolve(this@runTest) }
            f.gate.switch("other")
            f.gate.switch("player")
            assertAccountChanged(call.await().exceptionOrNull())
            assertTrue(f.requests.isEmpty())
            assertEquals(before, f.snapshot())
        }

    @Test
    fun sameUidWithANewEpochRejectsALateSuccessfulStatus() =
        runTest {
            val f = CancellationResolutionFixture()
            val before = f.snapshot()
            f.action = {
                f.gate.switch("other")
                f.gate.switch("player")
                Result.success(f.cancelled)
            }
            withTimeout(1000) { assertAccountChanged(f.resolve(this@runTest).exceptionOrNull()) }
            assertEquals(before, f.snapshot())
        }

    @Test
    fun lateFailedStatusRechecksTheOriginalEpochToo() =
        runTest {
            val f = CancellationResolutionFixture()
            val before = f.snapshot()
            f.action = {
                f.gate.switch("other")
                f.gate.switch("player")
                Result.failure(IllegalStateException("unavailable"))
            }
            assertAccountChanged(f.resolve(this).exceptionOrNull())
            assertEquals(before, f.snapshot())
        }

    @Test
    fun thrownAndResultCancellationEscapeWithoutRecordingFinancialFailure() =
        runTest {
            for (wrapped in listOf(false, true)) {
                val f = CancellationResolutionFixture()
                val before = f.snapshot()
                val original = CancellationException("screen stopped")
                f.action = { if (wrapped) Result.failure(original) else throw original }
                val actual = assertFailsWith<CancellationException> { f.resolve(this) }
                assertEquals(original::class, actual::class)
                assertEquals(original.message, actual.message)
                assertTrue(actual === original || actual.cause === original)
                assertEquals(before, f.snapshot())
            }
        }

    @Test
    fun transportFailureDoesNotResolveOrRewriteRetryMetadata() =
        runTest {
            val f = CancellationResolutionFixture()
            val before = f.snapshot()
            val failure = IllegalStateException("lost response")
            f.action = { Result.failure(failure) }
            val actual = assertIs<IllegalStateException>(f.resolve(this).exceptionOrNull())
            assertEquals(failure.message, actual.message)
            assertTrue(actual === failure || actual.cause === failure)
            assertEquals(before, f.snapshot())
        }

    @Test
    fun unknownTicketMissingIntentWrongOwnerAndUnverifiedLocalBodyDoNotContactStatus() =
        runTest {
            val changes: List<(CancellationResolutionFixture) -> Unit> =
                listOf(
                    { it.storage.tickets.clear() },
                    { it.storage.commands.clear() },
                    { it.replaceRow(it.row.copy(ownerUid = "other")) },
                    { it.replaceCommand(it.command.copy(bodySha256 = "0".repeat(64))) },
                    { it.replaceCommand(it.command.copy(state = "ACKNOWLEDGED")) },
                    { it.replaceCommand(it.command.copy(state = ARENA_RESOLVED_CANCELLED)) },
                    { it.replaceRow(it.row.copy(localUse = "UNUSED")) },
                    { it.replaceRow(it.row.copy(decision = "CANCEL")) },
                )
            for (change in changes) {
                val f = CancellationResolutionFixture().also(change)
                val before = f.snapshot()
                assertTrue(f.resolve(this).isFailure)
                assertTrue(f.requests.isEmpty())
                assertEquals(before, f.snapshot())
            }
            val f = CancellationResolutionFixture()
            assertAccountChanged(f.resolver(this).resolve("other", f.header.ticketId).exceptionOrNull())
            assertTrue(f.requests.isEmpty())
        }

    @Test
    fun missingAndUnsafeBanksAreNeverDecodedToResolveAGenuineCancellation() =
        runTest {
            for (bank in listOf(null, "{unsafe private payload")) {
                val f = CancellationResolutionFixture()
                f.replaceRow(f.row.copy(snapshotJson = bank, snapshotSha256 = null, checkpointJson = null))
                f.resolve(this).getOrThrow()
                assertEquals(bank, f.row.snapshotJson)
                assertNull(f.row.snapshotSha256)
                assertEquals(ARENA_RESOLVED_CANCELLED, f.retained.state)
            }
        }

    @Test
    fun retryAfterResolutionAndOrdinaryCleanupDoesNotRefreshTerminalOrRecreateMarker() =
        runTest {
            val f = CancellationResolutionFixture()
            f.resolve(this).getOrThrow()
            f.storage.firstIntents.clear()
            f.replaceRow(f.row.copy(snapshotJson = null, checkpointJson = null))
            val before = f.snapshot()
            f.now = 9000
            f.resolver(this).resolve("player", f.header.ticketId).getOrThrow()
            assertEquals(before, f.snapshot())
            assertEquals(
                f.cancelled.receipt.cancelledAtMs,
                Json.parseToJsonElement(f.row.terminalJson!!)
                    .jsonObject.getValue("cancelledAtMs").toString().toLong(),
            )
        }

    @Test
    fun noQueueOrMarkerAfterCleanupStillUsesOnlyTheRetainedFirstBody() =
        runTest {
            val f = CancellationResolutionFixture()
            f.storage.outbox.clear()
            f.storage.firstIntents.clear()
            f.resolve(this).getOrThrow()
            assertTrue(f.storage.outbox.isEmpty())
            assertEquals(f.first, f.storage.firstIntents[f.header.attemptId])
            assertEquals(f.command.bodyJson, f.retained.bodyJson)
        }

    @Test
    fun noncancelledResponsesCannotHideADurableLocalCancellation() =
        runTest {
            val f = CancellationResolutionFixture()
            f.resolve(this).getOrThrow()
            f.response = f.status("recoveredSelected")
            val before = f.snapshot()
            assertTrue(f.resolve(this).isFailure)
            assertEquals(before, f.snapshot())
        }

    @Test
    fun unchangedStatusStillRereadsLocalIdentityAfterTheAwait() =
        runTest {
            for (name in listOf("issuedSelected", "issuedHard")) {
                val f = CancellationResolutionFixture(name)
                f.response = f.status(if (name == "issuedHard") "recoveredSettled" else "recoveredSelected")
                var changed: List<Any>? = null
                f.action = {
                    f.replaceRow(f.row.copy(requestId = "mutated-request"))
                    changed = f.snapshot()
                    Result.success(f.response)
                }
                assertTrue(f.resolve(this).isFailure)
                assertEquals(changed, f.snapshot())
            }
        }

    @Test
    fun transactionRecheckRejectsALateChangedBodyWithoutApplyingTheObservation() =
        runTest {
            val f = CancellationResolutionFixture()
            var changed: List<Any>? = null
            val lateMutation =
                object : ArenaJournalStorage by f.storage {
                    override suspend fun resolveCancelledSettlement(
                        command: ArenaCommandEntity,
                        firstIntent: LessonAttemptIntentEntity,
                        observation: ArenaTicketRecordEntity,
                        validator: ArenaCancellationValidator,
                    ) {
                        f.replaceCommand(f.command.copy(createdAtMs = f.command.createdAtMs + 1))
                        changed = f.snapshot()
                        f.storage.resolveCancelledSettlement(command, firstIntent, observation, validator)
                    }
                }
            assertTrue(f.resolve(this, lateMutation).isFailure)
            assertEquals(changed, f.snapshot())
        }

    @Test
    fun parallelExplicitCallsPreserveOneTombstoneWithoutASecondDelivery() =
        runTest {
            val f = CancellationResolutionFixture()
            val resolver = f.resolver(this)
            val first = async { resolver.resolve("player", f.header.ticketId).getOrThrow() }
            val second = async { resolver.resolve("player", f.header.ticketId).getOrThrow() }
            assertEquals(first.await(), second.await())
            assertEquals(1, f.storage.commands.size)
            assertEquals(ARENA_RESOLVED_CANCELLED, f.retained.state)
            assertTrue(f.storage.outbox.isEmpty())
            assertEquals(f.command.bodyJson, f.retained.bodyJson)
        }

    @Test
    fun fakeTypedResponsesCannotBypassProofOrCauseAnyLocalMutation() =
        runTest {
            val f = CancellationResolutionFixture()
            val before = f.snapshot()
            val variants =
                listOf(
                    f.cancelled.copy(receipt = f.cancelled.receipt.copy(releasedNolics = 0)),
                    f.cancelled.copy(metadata = f.cancelled.metadata.copy(authorUid = "../bad")),
                    f.cancelled.copy(metadata = f.cancelled.metadata.copy(contentContext = null)),
                )
            for (response in variants) {
                f.response = response
                assertTrue(f.resolve(this).isFailure)
                assertEquals(before, f.snapshot())
            }
            val hard = CancellationResolutionFixture("issuedHard")
            val settled = assertIs<ArenaTicketStatusObservation.Settled>(hard.status("recoveredSettled"))
            hard.response = settled.copy(receipt = settled.receipt.copy(percentScore = Double.NaN))
            val unchanged = hard.snapshot()
            assertTrue(hard.resolve(this).isFailure)
            assertEquals(unchanged, hard.snapshot())
        }

    @Test
    fun genericReceiptAndQuarantineRemainAttentionInsteadOfBeingConsumedByStatus() =
        runTest {
            for (ack in listOf(false, true)) {
                val f = CancellationResolutionFixture()
                if (ack) {
                    f.storage.acknowledgedMutations += f.delivery.mutationId
                } else {
                    f.storage.outbox[f.delivery.mutationId] = f.delivery.copy(state = "QUARANTINED")
                }
                val before = f.snapshot()
                assertTrue(f.resolve(this).isFailure)
                assertEquals(before, f.snapshot())
            }
        }

    private fun assertAccountChanged(error: Throwable?) {
        assertEquals(ArenaRemoteFailureKind.ACCOUNT_CHANGED, assertIs<ArenaRemoteFailure>(error).kind)
    }
}

/** Shares the existing canonical journal storage/owner fakes and real server-exported question fixture. */
internal class CancellationResolutionFixture(name: String = "issuedSelected") {
    private val fixtures =
        javaClass.classLoader!!.getResourceAsStream("arena-ticket-client-fixtures.json")!!
            .bufferedReader().use { Json.parseToJsonElement(it.readText()).jsonObject }
    val parser = ArenaTicketWireParser(::cancellationSha)
    val codec = ArenaJournalCodec(::cancellationSha)
    val proof = ArenaCancelledSettlementProof(codec, ::cancellationSha)
    private val raw = fixtures.getValue(name).jsonObject.getValue("ticket").jsonObject
    val request = ArenaTicketGetRequest("player", raw.getValue("ticketId").toString().trim('"'))
    val ticket = parser.get(fixtures.getValue(name).toString(), request)
    val header = ticket.snapshot.header
    val original =
        codec.record(ticket, 100).copy(
            localUse = "LAUNCHED",
            decision = "SETTLE",
            checkpointJson = "retained-final-checkpoint",
            checkpointRevision = 4,
        )
    val command =
        codec.completion(
            original,
            UnscoredTerminalIntent(
                AttemptIdentity(
                    AttemptId(header.attemptId),
                    header.ownerUid,
                    LessonId(header.lessonId),
                    header.lessonVersion,
                    header.difficulty,
                ),
                AttemptCompletionKind.COMPLETED,
                header.issuedAtMs + 1000,
                emptyList(),
                emptyList(),
                ChargeClaimMask.none(ticket.snapshot.questions.size),
            ),
        )
    val delivery = codec.outbox(command).copy(id = 1, attemptCount = 3, nextRetryAtMs = 5000, lastError = "retry")
    val first =
        LessonAttemptIntentEntity(
            header.attemptId,
            delivery.ownerUid,
            delivery.mutationId,
            delivery.operation,
            delivery.payload,
            delivery.entityRef,
            delivery.expectedVersion,
        )
    val storage =
        JournalStorageFake().apply {
            tickets[header.ownerUid to header.ticketId] = original
            commands[Triple(header.ownerUid, "SETTLE", header.attemptId)] = command
            outbox[delivery.mutationId] = delivery
            firstIntents[header.attemptId] = first
        }
    val gate = JournalGateFake()
    val cancelled =
        ArenaTicketStatusObservation.Cancelled(
            ArenaTicketStatusMetadata(
                header.copy(status = ArenaTicketStatus.CANCELLED),
                ticket.snapshot.authorUid,
                ticket.snapshot.ratingVersion,
                ticket.snapshot.contentContext,
            ),
            ArenaCancellationReceipt(
                "arena-cancel:${header.ticketId}",
                header.issuedAtMs,
                header.priceNolics,
                header.lifeCost,
            ),
        )
    var response: ArenaTicketStatusObservation = cancelled
    val requests = mutableListOf<ArenaTicketGetRequest>()
    var action: suspend () -> Result<ArenaTicketStatusObservation> = { Result.success(response) }
    private val remote =
        object : ArenaTicketStatusRemoteDataSource {
            override suspend fun status(request: ArenaTicketGetRequest): Result<ArenaTicketStatusObservation> {
                requests += request
                return action()
            }
        }
    var now = 200L
    val row get() = storage.tickets.getValue(header.ownerUid to header.ticketId)
    val retained get() = storage.commands.getValue(Triple(header.ownerUid, "SETTLE", header.attemptId))

    fun replaceRow(row: ArenaTicketRecordEntity) {
        storage.tickets[header.ownerUid to header.ticketId] = row
    }

    fun replaceCommand(command: ArenaCommandEntity) {
        storage.commands[Triple(header.ownerUid, "SETTLE", header.attemptId)] = command
    }

    fun snapshot(): List<Any> =
        listOf(
            storage.tickets.toMap(),
            storage.commands.toMap(),
            storage.outbox.toMap(),
            storage.firstIntents.toMap(),
            storage.acknowledgedMutations.toSet(),
            storage.scoredAttempts.toSet(),
        )

    fun resolver(
        scope: TestScope,
        actualStorage: ArenaJournalStorage = storage,
    ) = ArenaCancelledSettlementResolver(
        actualStorage,
        remote,
        gate,
        proof,
        StandardTestDispatcher(scope.testScheduler),
        { now },
    )

    suspend fun resolve(
        scope: TestScope,
        actualStorage: ArenaJournalStorage = storage,
    ) = resolver(scope, actualStorage).resolve(header.ownerUid, header.ticketId)

    fun status(name: String): ArenaTicketStatusObservation {
        val fields =
            fixtures.getValue(
                name,
            ).jsonObject.getValue("ticket").jsonObject - setOf("questions", "requestHash")
        val wire = JsonObject(mapOf("ticket" to JsonObject(fields)))
        return parser.status(
            wire.toString(),
            ArenaTicketGetRequest(
                "player",
                fields.getValue("ticketId")
                    .toString().trim('"'),
            ),
        )
    }

    fun body(
        changes: Map<String, JsonPrimitive>,
        original: ArenaCommandEntity = command,
    ): ArenaCommandEntity {
        val body = JsonObject(Json.parseToJsonElement(original.bodyJson).jsonObject + changes).toString()
        return original.copy(bodyJson = body, bodySha256 = cancellationSha(body))
    }
}

internal fun cancellationSha(value: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
