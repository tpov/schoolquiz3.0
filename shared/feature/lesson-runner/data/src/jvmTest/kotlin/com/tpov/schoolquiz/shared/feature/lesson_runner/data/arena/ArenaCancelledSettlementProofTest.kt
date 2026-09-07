@file:Suppress("MagicNumber") // Exact fixture expectations and malicious boundary values.

package com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena

import com.tpov.schoolquiz.shared.core.persistence.ARENA_RESOLVED_CANCELLED
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaCancelResponse
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaEntryMode
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketGetRequest
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketStatus
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketStatusObservation
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertIs
import kotlin.test.assertNull

class ArenaCancelledSettlementProofTest {
    @Test
    fun canonicalCancellationIsByteIdenticalToLegacyGetAndCancelWithoutAStoredBank() {
        val f = CancellationResolutionFixture()
        val actualStatus = assertIs<ArenaTicketStatusObservation.Cancelled>(f.status("recoveredCancelled"))
        assertEquals(actualStatus, f.cancelled)
        val bound = bind(f)
        val legacy =
            f.codec.record(
                com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaEntryTicket.Cancelled(
                    f.ticket.snapshot.copy(header = f.header.copy(status = ArenaTicketStatus.CANCELLED)),
                    actualStatus.receipt,
                ),
                200,
            )
        val oldCancel =
            f.codec.terminal(
                f.original,
                ArenaCancelResponse.Cancelled(f.header.ticketId, actualStatus.receipt),
                200,
            )
        val observation = bound.observation
        assertEquals(legacy.termsJson, observation.termsJson)
        assertEquals(legacy.terminalJson, observation.terminalJson)
        assertEquals(oldCancel.terminalJson, observation.terminalJson)
        assertEquals(f.header.ownerUid, observation.ownerUid)
        assertEquals(f.header.ticketId, observation.ticketId)
        assertEquals(f.header.attemptId, observation.attemptId)
        assertEquals(f.header.requestId, observation.requestId)
        assertEquals("CANCELLED", observation.serverStatus)
        assertEquals("UNUSED", observation.localUse)
        assertEquals("NONE", observation.decision)
        assertEquals(200L, observation.observedAtMs)
        assertEquals(0L, observation.checkpointRevision)
        assertNull(observation.snapshotJson)
        assertNull(observation.snapshotSha256)
        assertNull(observation.checkpointJson)
        bound.validate(f.original, f.command, f.first, observation)
    }

    @Test
    fun everyFakeCancellationReceiptFieldIsRevalidatedAgainstTheFrozenHold() {
        val f = CancellationResolutionFixture()
        val r = f.cancelled.receipt
        val variants =
            listOf(
                r.copy(receiptId = "arena-cancel:other"),
                r.copy(cancelledAtMs = f.header.issuedAtMs - 1),
                r.copy(cancelledAtMs = Long.MAX_VALUE),
                r.copy(releasedNolics = 0), r.copy(releasedNolics = r.releasedNolics + 1),
                r.copy(releasedNolics = -1), r.copy(releasedLifePoints = 0),
                r.copy(releasedLifePoints = r.releasedLifePoints + 1), r.copy(releasedLifePoints = Long.MAX_VALUE),
            )
        for (receipt in variants) assertFails { bind(f, f.cancelled.copy(receipt = receipt)) }
    }

    @Test
    fun everyImmutableHeaderFieldAndTheRequestedIdentityIsCheckedAgain() {
        val f = CancellationResolutionFixture()
        val h = f.cancelled.metadata.header
        val variants =
            listOf(
                h.copy(schemaVersion = 2), h.copy(ticketId = "0".repeat(64)), h.copy(attemptId = "0".repeat(64)),
                h.copy(ownerUid = "other"), h.copy(requestId = "other"), h.copy(mode = ArenaEntryMode.RANDOM),
                h.copy(difficulty = Difficulty.HARD), h.copy(lessonId = "other"), h.copy(lessonVersion = 4),
                h.copy(priceNolics = 0), h.copy(lifeCost = 49), h.copy(issuedAtMs = h.issuedAtMs - 1),
                h.copy(dayId = "2026-09-07"),
            )
        for (header in variants) assertFails {
            bind(f, f.cancelled.copy(metadata = f.cancelled.metadata.copy(header = header)))
        }
        assertFails {
            f.proof.bind(
                f.cancelled,
                ArenaTicketGetRequest("other", h.ticketId),
                f.original,
                f.command,
                200,
            )
        }
        assertFails {
            f.proof.bind(
                f.cancelled,
                ArenaTicketGetRequest("player", "f".repeat(64)),
                f.original,
                f.command,
                200,
            )
        }
    }

    @Test
    fun syntacticallyValidChangedPriceWithAMatchingRefundStillCannotReplaceLocalTerms() {
        val f = CancellationResolutionFixture()
        val changed =
            f.cancelled.copy(
                metadata = f.cancelled.metadata.copy(header = f.cancelled.metadata.header.copy(priceNolics = 11)),
                receipt = f.cancelled.receipt.copy(releasedNolics = 11),
            )
        assertFails { bind(f, changed) }
    }

    @Test
    fun metadataAndContextMustBeValidAndMatchTheOriginalSerializedContext() {
        val f = CancellationResolutionFixture()
        val m = f.cancelled.metadata
        val context = requireNotNull(m.contentContext)
        val variants =
            listOf(
                m.copy(authorUid = "../other"), m.copy(authorUid = ""), m.copy(ratingVersion = -1),
                m.copy(ratingVersion = Long.MAX_VALUE), m.copy(contentContext = null),
                m.copy(contentContext = context.copy(catalogId = "other")),
                m.copy(contentContext = context.copy(questId = "other")),
                m.copy(contentContext = context.copy(sectionId = "other")),
                m.copy(contentContext = context.copy(themeId = "other")),
                m.copy(contentContext = context.copy(themeId = "../bad")),
            )
        for (metadata in variants) assertFails { bind(f, f.cancelled.copy(metadata = metadata)) }
    }

    @Test
    fun absentOriginalContextMatchesOnlyExplicitNullMetadataWithoutReadingTheBank() {
        val f = CancellationResolutionFixture()
        val body =
            JsonObject(
                Json.parseToJsonElement(f.command.bodyJson).jsonObject -
                    setOf("catalogId", "questId", "sectionId", "themeId"),
            ).toString()
        val command = f.command.copy(bodyJson = body, bodySha256 = cancellationSha(body))
        val row = f.original.copy(snapshotJson = null, snapshotSha256 = null)
        val response = f.cancelled.copy(metadata = f.cancelled.metadata.copy(contentContext = null))
        val bound = f.proof.bind(response, f.request, row, command, 0)
        bound.validate(row, command, bound.firstIntent, bound.observation)
        assertFails { f.proof.bind(f.cancelled, f.request, row, command, 0) }
    }

    @Test
    fun everySerializedIdentityRefusesEvenWhenATamperedBodyHasAValidNewHash() {
        val f = CancellationResolutionFixture()
        val changes =
            mapOf(
                "ownerUid" to JsonPrimitive("other"), "userId" to JsonPrimitive("other"),
                "ticketId" to JsonPrimitive("other"), "attemptId" to JsonPrimitive("other"),
                "lessonId" to JsonPrimitive("other"), "lessonVersion" to JsonPrimitive(4),
                "difficulty" to JsonPrimitive("HARD"), "scope" to JsonPrimitive("private"),
                "sourceShelf" to JsonPrimitive("course"), "completionKind" to JsonPrimitive("ABORTED"),
                "createdAtMs" to JsonPrimitive(0), "completedAtMs" to JsonPrimitive(0),
            )
        for ((key, value) in changes) assertFails { f.proof.firstIntent(f.original, f.body(mapOf(key to value))) }
        assertFails { f.proof.firstIntent(f.original, f.command.copy(bodySha256 = "0".repeat(64))) }
        assertFails { f.proof.firstIntent(f.original, f.command.copy(createdAtMs = f.command.createdAtMs + 1)) }
    }

    @Test
    fun partialContextUnknownFieldsNoncanonicalDuplicateKeysAndOversizeBodiesFailClosed() {
        val f = CancellationResolutionFixture()
        val body = Json.parseToJsonElement(f.command.bodyJson).jsonObject
        val variants =
            listOf(
                JsonObject(body - "catalogId").toString(),
                JsonObject(body + ("percentScore" to JsonPrimitive(100))).toString(),
                " " + f.command.bodyJson,
                f.command.bodyJson.dropLast(1) + ",\"ownerUid\":\"player\"}",
                " ".repeat(500_001),
            )
        for (value in variants) assertFails {
            f.proof.firstIntent(f.original, f.command.copy(bodyJson = value, bodySha256 = cancellationSha(value)))
        }
    }

    @Test
    fun transactionProofRejectsAnyChangedObservationOrFirstIntentField() {
        val f = CancellationResolutionFixture()
        val bound = bind(f)
        val o = bound.observation
        val observations =
            listOf(
                o.copy(ownerUid = "other"), o.copy(ticketId = "other"), o.copy(attemptId = "other"),
                o.copy(requestId = "other"), o.copy(termsJson = "{}"), o.copy(serverStatus = "SETTLED"),
                o.copy(terminalJson = "{}"), o.copy(observedAtMs = 201), o.copy(snapshotJson = "bank"),
                o.copy(snapshotSha256 = "hash"), o.copy(localUse = "LAUNCHED"), o.copy(decision = "SETTLE"),
                o.copy(checkpointJson = "0"), o.copy(checkpointRevision = 1),
            )
        for (observation in observations) assertFails { bound.validate(f.original, f.command, f.first, observation) }
        val i = f.first
        val intents =
            listOf(
                i.copy(ownerUid = "other"),
                i.copy(attemptId = "other"),
                i.copy(mutationId = "other"),
                i.copy(operation = "lesson_runner.SUBMIT_RATING"),
                i.copy(payload = "{}"),
                i.copy(entityRef = "lesson_runner:attempt:other"),
                i.copy(expectedVersion = 1),
            )
        for (intent in intents) assertFails { bound.validate(f.original, f.command, intent, o) }
    }

    @Test
    fun transactionProofRechecksTheRereadTicketCommandAndBodyRatherThanCapturedRows() {
        val f = CancellationResolutionFixture()
        val bound = bind(f)
        val rows =
            listOf(
                f.original.copy(ownerUid = "other"),
                f.original.copy(ticketId = "other"),
                f.original.copy(attemptId = "other"),
                f.original.copy(requestId = "other"),
                f.original.copy(localUse = "UNUSED"),
                f.original.copy(decision = "CANCEL"),
                f.original.copy(termsJson = "{}"),
                f.original.copy(terminalJson = "unproven"),
            )
        for (row in rows) assertFails { bound.validate(row, f.command, f.first, bound.observation) }
        val commands =
            listOf(
                f.command.copy(ownerUid = "other"),
                f.command.copy(ticketId = "other"),
                f.command.copy(attemptId = "other"),
                f.command.copy(commandId = "other"),
                f.command.copy(commandKind = "CANCEL"),
                f.command.copy(bodySha256 = "0".repeat(64)),
                f.command.copy(createdAtMs = f.command.createdAtMs + 1),
                f.body(mapOf("userId" to JsonPrimitive("other"))),
            )
        for (command in commands) assertFails { bound.validate(f.original, command, f.first, bound.observation) }
    }

    @Test
    fun validDeliveryStateProgressDoesNotChangeTheFrozenProofButAnAckIsNotResolution() {
        val f = CancellationResolutionFixture()
        val bound = bind(f)
        bound.validate(
            f.original,
            f.command.copy(state = "ATTENTION", lastFailure = "retry"),
            f.first,
            bound.observation,
        )
        val terminal = f.original.copy(serverStatus = "CANCELLED", terminalJson = bound.observation.terminalJson)
        bound.validate(terminal, f.command.copy(state = ARENA_RESOLVED_CANCELLED), f.first, bound.observation)
        for (state in listOf("ACKNOWLEDGED", "UNKNOWN", "RESOLVED")) assertFails {
            bound.validate(f.original, f.command.copy(state = state), f.first, bound.observation)
        }
    }

    @Test
    fun backwardsOrExpiredLocalClockNeverReplacesTheAuthoritativeReceiptTimestamp() {
        val f = CancellationResolutionFixture()
        for (localTime in listOf(0L, 200L, f.header.issuedAtMs + 1_000_000)) {
            val bound = f.proof.bind(f.cancelled, f.request, f.original, f.command, localTime)
            bound.validate(f.original, f.command, f.first, bound.observation)
            assertEquals(localTime, bound.observation.observedAtMs)
            assertEquals(
                JsonPrimitive(f.cancelled.receipt.cancelledAtMs),
                Json.parseToJsonElement(
                    bound.observation.terminalJson!!,
                ).jsonObject.getValue("cancelledAtMs"),
            )
        }
        assertFails { f.proof.bind(f.cancelled, f.request, f.original, f.command, -1) }
        assertFails {
            bind(
                f,
                f.cancelled.copy(receipt = f.cancelled.receipt.copy(cancelledAtMs = f.header.issuedAtMs - 1)),
            )
        }
    }

    @Test
    fun twoBoundInvocationsCannotSubstituteEachOthersImmutableObservation() {
        val first = CancellationResolutionFixture()
        val second = CancellationResolutionFixture("issuedRandom")
        val shared = first.proof
        val a = shared.bind(first.cancelled, first.request, first.original, first.command, 200)
        val b = shared.bind(second.cancelled, second.request, second.original, second.command, 200)
        b.validate(second.original, second.command, second.first, b.observation)
        a.validate(first.original, first.command, first.first, a.observation)
        assertFails { a.validate(first.original, first.command, first.first, b.observation) }
        assertFails { b.validate(first.original, first.command, first.first, a.observation) }
    }

    @Test
    fun noncancelledBoundValuesCanNeverBeUsedAsAStorageResolutionValidator() {
        val f = CancellationResolutionFixture()
        val reserved = f.proof.bind(f.status("recoveredSelected"), f.request, f.original, f.command, 200)
        reserved.checkCurrent(f.original, f.command)
        assertFails { reserved.validate(f.original, f.command, f.first, reserved.observation) }
        val hard = CancellationResolutionFixture("issuedHard")
        val settled = hard.proof.bind(hard.status("recoveredSettled"), hard.request, hard.original, hard.command, 200)
        settled.checkCurrent(hard.original, hard.command)
        assertFails { settled.validate(hard.original, hard.command, hard.first, settled.observation) }
    }

    @Test
    fun contradictoryOrDifferentlySerializedLocalTerminalIsNotSilentlyOverwritten() {
        val f = CancellationResolutionFixture()
        val bound = bind(f)
        assertFails { bound.checkCurrent(f.original.copy(serverStatus = "SETTLED", terminalJson = "{}"), f.command) }
        val reversed =
            JsonObject(
                Json.parseToJsonElement(bound.observation.terminalJson!!).jsonObject
                    .entries.reversed().associate { it.toPair() },
            ).toString()
        assertFails {
            bound.checkCurrent(
                f.original.copy(serverStatus = "CANCELLED", terminalJson = reversed),
                f.command,
            )
        }
    }

    @Test
    fun wrongRequiredDigestProviderHasNoPermissiveFallback() {
        val f = CancellationResolutionFixture()
        val broken = ArenaCancelledSettlementProof(f.codec) { "0".repeat(64) }
        assertFails { broken.bind(f.cancelled, f.request, f.original, f.command, 200) }
    }

    @Test
    fun zeroNolicsAreAcceptedOnlyForAGenuineRandomHoldAndNeverAsAMissingPaidRefund() {
        val random = CancellationResolutionFixture("issuedRandom")
        val bound = bind(random)
        assertEquals(0, random.cancelled.receipt.releasedNolics)
        bound.validate(random.original, random.command, random.first, bound.observation)
        val selected = CancellationResolutionFixture()
        assertFails {
            bind(
                selected,
                selected.cancelled.copy(receipt = selected.cancelled.receipt.copy(releasedNolics = 0)),
            )
        }
    }

    private fun bind(
        f: CancellationResolutionFixture,
        response: ArenaTicketStatusObservation = f.cancelled,
    ) = f.proof.bind(response, f.request, f.original, f.command, 200)
}
