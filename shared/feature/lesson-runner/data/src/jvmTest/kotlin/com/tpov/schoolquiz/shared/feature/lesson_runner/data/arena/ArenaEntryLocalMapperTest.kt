@file:Suppress("MagicNumber") // Frozen wire fixture values and deliberate corruption cases.

package com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena

import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ArenaTicketWireParser
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.repository.EntryFacadeFixture
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaCancelResponse
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLocalEntryState
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaPreparationReason
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaPreparationState
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaPrepareRequest
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketStatus
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketStatusObservation
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class ArenaEntryLocalMapperTest {
    @Test
    fun preparedSummaryProjectsEveryHeaderFieldWithoutQuestionOrCheckpointAuthority() {
        val f = EntryFacadeFixture()
        val entry = f.mapper.ticket("player", f.row)
        val h = assertNotNull(entry.header)
        val expected = f.header
        assertEquals(expected.ticketId, entry.ticketId)
        assertEquals(expected.schemaVersion, h.schemaVersion)
        assertEquals(expected.ticketId, h.ticketId)
        assertEquals(expected.attemptId, h.attemptId)
        assertEquals(expected.ownerUid, h.ownerUid)
        assertEquals(expected.requestId, h.requestId)
        assertEquals(expected.mode, h.mode)
        assertEquals(expected.difficulty, h.difficulty)
        assertEquals(expected.lessonId, h.lessonId)
        assertEquals(expected.lessonVersion, h.lessonVersion)
        assertEquals(expected.status, h.status)
        assertEquals(expected.priceNolics, h.priceNolics)
        assertEquals(expected.lifeCost, h.lifeCost)
        assertEquals(expected.issuedAtMs, h.issuedAtMs)
        assertEquals(expected.dayId, h.dayId)
        assertEquals(ArenaLocalEntryState.PREPARED_SNAPSHOT, entry.state)
        assertFalse(entry.toString().contains("correctOptionId"))
        assertFalse(entry.toString().contains("checkpointJson"))
    }

    @Test
    fun missingOrCorruptSnapshotStaysVisibleAsNeedsRecoveryWithItsValidatedHeader() {
        val f = EntryFacadeFixture()
        for (row in listOf(
            f.row.copy(snapshotJson = null, snapshotSha256 = null),
            f.row.copy(snapshotSha256 = "not the hash"),
            f.row.copy(snapshotJson = "private-answer-secret"),
            f.row.copy(snapshotJson = "{}", snapshotSha256 = cancellationSha("{}")),
        )) {
            val entry = f.mapper.ticket("player", row)
            assertEquals(f.header.ticketId, entry.ticketId)
            assertEquals(f.header, entry.header)
            assertEquals(ArenaLocalEntryState.NEEDS_RECOVERY, entry.state)
            assertFalse(entry.toString().contains("private-answer-secret"))
        }
    }

    @Test
    fun unknownOrContradictoryLocalStateNeverFallsBackToAnUnusedPreparedTicket() {
        val f = EntryFacadeFixture()
        for (row in listOf(
            f.row.copy(localUse = "FUTURE"),
            f.row.copy(decision = "FUTURE"),
            f.row.copy(decision = "SETTLE"),
            f.row.copy(checkpointJson = "0"),
            f.row.copy(checkpointRevision = 1),
            f.row.copy(checkpointRevision = -1),
            f.row.copy(observedAtMs = -1),
            f.row.copy(terminalJson = "{}"),
        )) {
            val entry = f.mapper.ticket("player", row)
            assertEquals(ArenaLocalEntryState.NEEDS_RECOVERY, entry.state)
            assertEquals(f.header, entry.header)
        }
    }

    @Test
    fun launchedSummaryNeverOffersRestartEvenWhenItsCheckpointNeedsReconciliation() {
        val f = EntryFacadeFixture()
        for (checkpoint in listOf(null, "0", "unknown-checkpoint-private")) {
            val entry = f.mapper.ticket("player", f.row.copy(localUse = "LAUNCHED", checkpointJson = checkpoint))
            assertEquals(ArenaLocalEntryState.RESUME_OR_RECONCILE, entry.state)
            assertFalse(entry.toString().contains("unknown-checkpoint-private"))
        }
    }

    @Test
    fun pendingDecisionsStayPendingWithoutTryingToParseAnUnsafeBank() {
        val f = EntryFacadeFixture()
        for ((decision, expected) in listOf(
            "CANCEL" to ArenaLocalEntryState.CANCEL_PENDING,
            "SETTLE" to ArenaLocalEntryState.SETTLEMENT_PENDING,
        )) {
            val entry =
                f.mapper.ticket(
                    "player",
                    f.row.copy(localUse = "LAUNCHED", decision = decision, snapshotJson = "private unsafe bank"),
                )
            assertEquals(expected, entry.state)
            assertEquals(ArenaTicketStatus.RESERVED, entry.header?.status)
            assertFalse(entry.toString().contains("private unsafe bank"))
        }
    }

    @Test
    fun cancelledAndSettledHistoryNeedsNoSnapshotAndDoesNotExposeReceiptScoreOrReward() {
        for (name in listOf("issuedSelected", "issuedHard")) {
            val f = CancellationResolutionFixture(name)
            val observation = if (name == "issuedSelected") f.cancelled else f.status("recoveredSettled")
            val response =
                when (observation) {
                    is ArenaTicketStatusObservation.Cancelled ->
                        ArenaCancelResponse.Cancelled(
                            f.header.ticketId,
                            observation.receipt,
                        )
                    is ArenaTicketStatusObservation.Settled ->
                        ArenaCancelResponse.AlreadySettled(
                            f.header.ticketId,
                            observation.receipt,
                        )
                    else -> error("Expected a terminal fixture")
                }
            val terminal = f.codec.terminal(f.original, response, 200)
            for (bank in listOf(null, "unsafe future private keys")) {
                val entry =
                    ArenaEntryLocalMapper(f.codec, f.parser).ticket(
                        "player",
                        terminal.copy(snapshotJson = bank, snapshotSha256 = null),
                    )
                assertEquals(ArenaLocalEntryState.TERMINAL_OBSERVED, entry.state)
                assertEquals(observation.metadata.header, entry.header)
                assertFalse(entry.toString().contains("percentScore"))
                assertFalse(entry.toString().contains("receiptId"))
                assertFalse(entry.toString().contains("private keys"))
            }
        }
    }

    @Test
    fun terminalObservationWithMissingWrongIdentityOrBrokenConservationRemainsRecoveryOnly() {
        val f = EntryFacadeFixture()
        val terminal =
            f.codec.terminal(
                f.row,
                ArenaCancelResponse.Cancelled(f.header.ticketId, f.base.cancelled.receipt),
                200,
            )
        val fields = Json.parseToJsonElement(assertNotNull(terminal.terminalJson)).jsonObject
        for (proof in listOf(
            null,
            "{}",
            JsonObject(fields + ("receiptId" to JsonPrimitive("arena-cancel:other"))).toString(),
            JsonObject(fields + ("releasedNolics" to JsonPrimitive(0))).toString(),
            JsonObject(fields + ("releasedLifePoints" to JsonPrimitive(0))).toString(),
            JsonObject(fields + ("cancelledAtMs" to JsonPrimitive(-1))).toString(),
        )) {
            val entry = f.mapper.ticket("player", terminal.copy(terminalJson = proof))
            assertEquals(ArenaLocalEntryState.NEEDS_RECOVERY, entry.state)
            assertEquals(ArenaTicketStatus.CANCELLED, entry.header?.status)
        }
    }

    @Test
    fun identityTermsAndUnknownStatusCorruptionRetainOnlyTheLocalIdNotAnInventedHeader() {
        val f = EntryFacadeFixture()
        val terms = Json.parseToJsonElement(f.row.termsJson).jsonObject
        for (row in listOf(
            f.row.copy(termsJson = "{broken secret"),
            f.row.copy(serverStatus = "ISSUED"),
            f.row.copy(attemptId = "0".repeat(64)),
            f.row.copy(requestId = "another-request"),
            f.row.copy(termsJson = JsonObject(terms + ("priceNolics" to JsonPrimitive(0))).toString()),
            f.row.copy(termsJson = JsonObject(terms + ("privateFuture" to JsonPrimitive("private"))).toString()),
        )) {
            val entry = f.mapper.ticket("player", row)
            assertEquals(row.ticketId, entry.ticketId)
            assertNull(entry.header)
            assertEquals(ArenaLocalEntryState.NEEDS_RECOVERY, entry.state)
        }
    }

    @Test
    fun preparedConsentProjectsEveryExplicitFieldAndNeverCreatesADefaultCeiling() {
        val f = EntryFacadeFixture()
        val command = f.codec.consent(f.selected, 100)
        val entry = f.mapper.preparation("player", command)
        val request = assertIs<ArenaPrepareRequest.Selected>(entry.request)
        assertEquals(f.selected.requestId, entry.requestId)
        assertEquals(f.selected.ownerUid, request.ownerUid)
        assertEquals(f.selected.requestId, request.requestId)
        assertEquals(f.selected.difficulty, request.difficulty)
        assertEquals(f.selected.lessonId, request.lessonId)
        assertEquals(f.selected.maxPriceNolics, request.maxPriceNolics)
        assertEquals(ArenaPreparationState.PENDING, entry.state)
        assertEquals(ArenaPreparationReason.RETRY_SAME_REQUEST, entry.reason)
        val attention = f.mapper.preparation("player", command.copy(state = "ATTENTION", lastFailure = "PRICE_CHANGED"))
        assertEquals(request, attention.request)
        assertEquals(ArenaPreparationReason.PRICE_CHANGED_REQUIRES_CONSENT, attention.reason)
    }

    @Test
    fun corruptConsentAndUnknownCommandStateRemainVisibleWithoutAnExecutableRequest() {
        val f = EntryFacadeFixture()
        val command = f.codec.consent(f.selected, 100)
        for (row in listOf(
            command.copy(bodyJson = "{private"),
            command.copy(bodySha256 = "changed"),
            command.copy(commandId = "another"),
            command.copy(commandKind = "CANCEL"),
            command.copy(ticketId = f.header.ticketId),
            command.copy(attemptId = f.header.attemptId),
            command.copy(state = "FUTURE"),
            command.copy(state = "ACKNOWLEDGED"),
            command.copy(createdAtMs = -1),
            command.copy(lastFailure = "PRICE_CHANGED"),
        )) {
            val entry = f.mapper.preparation("player", row)
            assertEquals(row.commandId, entry.requestId)
            assertNull(entry.request)
            assertEquals(ArenaPreparationState.NEEDS_RECOVERY, entry.state)
            assertEquals(ArenaPreparationReason.NEEDS_RECOVERY, entry.reason)
            assertFalse(entry.toString().contains("{private"))
        }
    }

    @Test
    fun unknownFailureReasonNeverLeaksItsStoredMessageOrChangesTheOriginalConsent() {
        val f = EntryFacadeFixture()
        val command = f.codec.consent(f.selected, 100)
        for (state in listOf("PENDING", "ATTENTION")) {
            val entry =
                f.mapper.preparation(
                    "player",
                    command.copy(state = state, lastFailure = "private SQL and answer"),
                )
            assertEquals(f.selected, entry.request)
            assertEquals(ArenaPreparationReason.OTHER_ATTENTION, entry.reason)
            assertFalse(entry.toString().contains("private SQL"))
        }
    }

    @Test
    fun foreignRowsAreRejectedBeforeEvenTheirIdentifiersCanBecomeDomainValues() {
        val f = EntryFacadeFixture()
        assertFailsWith<IllegalArgumentException> { f.mapper.ticket("other", f.row) }
        assertFailsWith<IllegalArgumentException> {
            f.mapper.preparation("other", f.codec.consent(f.selected, 100))
        }
    }

    @Test
    fun pureProjectionDoesNotSwallowCoroutineCancellationFromARequiredDependency() {
        val f = EntryFacadeFixture()
        val cancelled = CancellationException("keep structured control flow")
        val mapper =
            ArenaEntryLocalMapper(ArenaJournalCodec { throw cancelled }, ArenaTicketWireParser(::cancellationSha))
        assertSame(cancelled, assertFailsWith<CancellationException> { mapper.ticket("player", f.row) })
    }
}
