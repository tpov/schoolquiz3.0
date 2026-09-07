@file:Suppress("MagicNumber") // Fixed protocol fixture values and explicit malformed boundaries.

package com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote

import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.core.question_schema.QuestionContent
import com.tpov.schoolquiz.shared.core.question_schema.QuestionContentParser
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaEntryMode
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaEntryTicket
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRemoteFailure
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRemoteFailureKind
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketGetRequest
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketStatus
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketStatusObservation
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class ArenaTicketStatusWireParserTest {
    private val fixtures =
        javaClass.classLoader!!.getResourceAsStream("arena-ticket-client-fixtures.json")!!
            .bufferedReader().use { Json.parseToJsonElement(it.readText()).jsonObject }
    private val parser = ArenaTicketWireParser(::sha)

    @Test
    fun reservedObservationPreservesEveryMetadataFieldWithoutASnapshot() {
        val raw = ticketFixture("recoveredSelected")
        val value = assertIs<ArenaTicketStatusObservation.Reserved>(parse(raw))
        val metadata = value.metadata
        val header = metadata.header
        assertEquals(1, header.schemaVersion)
        assertEquals(raw.text("ticketId"), header.ticketId)
        assertEquals(raw.text("attemptId"), header.attemptId)
        assertEquals("player", header.ownerUid)
        assertEquals("client-selected", header.requestId)
        assertEquals(ArenaEntryMode.SELECTED, header.mode)
        assertEquals(Difficulty.EASY, header.difficulty)
        assertEquals("lesson", header.lessonId)
        assertEquals(3L, header.lessonVersion)
        assertEquals(ArenaTicketStatus.RESERVED, header.status)
        assertEquals(10, header.priceNolics)
        assertEquals(50L, header.lifeCost)
        assertEquals(1_788_696_000_000L, header.issuedAtMs)
        assertEquals("2026-09-06", header.dayId)
        assertEquals("author", metadata.authorUid)
        assertEquals(5L, metadata.ratingVersion)
        val context = requireNotNull(metadata.contentContext)
        assertEquals("catalog", context.catalogId)
        assertEquals("quest", context.questId)
        assertEquals("section", context.sectionId)
        assertEquals("theme", context.themeId)
    }

    @Test
    fun cancelledObservationCarriesTheActualFullRefundReceipt() {
        val raw = ticketFixture("recoveredCancelled")
        val value = assertIs<ArenaTicketStatusObservation.Cancelled>(parse(raw))
        assertEquals(ArenaTicketStatus.CANCELLED, value.metadata.header.status)
        assertEquals("arena-cancel:" + raw.text("ticketId"), value.receipt.receiptId)
        assertEquals(
            raw.getValue("terminal").jsonObject.getValue("cancelledAtMs").jsonPrimitive.content.toLong(),
            value.receipt.cancelledAtMs,
        )
        assertEquals(10, value.receipt.releasedNolics)
        assertEquals(50L, value.receipt.releasedLifePoints)
    }

    @Test
    fun settledObservationKeepsFractionalScoreAndEveryConservedReceiptTerm() {
        val raw = ticketFixture("recoveredSettled")
        val value = assertIs<ArenaTicketStatusObservation.Settled>(parse(raw))
        val receipt = value.receipt
        assertEquals(ArenaTicketStatus.SETTLED, value.metadata.header.status)
        assertEquals("arena-settle:" + raw.text("ticketId"), receipt.receiptId)
        assertEquals(raw.text("attemptId"), receipt.attemptId)
        assertEquals(1_788_696_000_000L, receipt.settledAtMs)
        assertEquals(75.5, receipt.percentScore)
        assertEquals(10, receipt.consumedNolics)
        assertEquals(50L, receipt.consumedLifePoints)
        assertEquals(8L, receipt.authorCreditNolics)
        assertEquals(2L, receipt.sinkNolics)
        assertEquals(0L, receipt.royaltyReserveDeltaNolics)
        val zero = patchTerminal(raw, "percentScore", JsonPrimitive(0))
        assertEquals(0.0, assertIs<ArenaTicketStatusObservation.Settled>(parse(zero)).receipt.percentScore)
        val carry =
            patchTerminal(
                patchTerminal(raw, "authorCreditNolics", JsonPrimitive(9)),
                "royaltyReserveDeltaNolics",
                JsonPrimitive(-1),
            )
        val carriedReceipt = assertIs<ArenaTicketStatusObservation.Settled>(parse(carry)).receipt
        assertEquals(9L, carriedReceipt.authorCreditNolics)
        assertEquals(-1L, carriedReceipt.royaltyReserveDeltaNolics)
    }

    @Test
    fun everyRequiredMetadataFieldAndTheTerminalFieldMustActuallyExist() {
        val raw = ticketFixture("recoveredSelected")
        assertEquals(18, raw.size)
        for (field in raw.keys) malformed(JsonObject(raw - field))
        malformedWire("{}")
        malformedWire("{\"ticket\":null}")
    }

    @Test
    fun ownerTicketAttemptAndIssuerIdentityAreBoundToTheOriginalRequest() {
        val raw = ticketFixture("recoveredSelected")
        malformed(raw, ArenaTicketGetRequest("other", raw.text("ticketId")))
        malformed(raw, ArenaTicketGetRequest("player", "f".repeat(64)))
        for ((field, value) in listOf(
            "uid" to "other",
            "ticketId" to "f".repeat(64),
            "attemptId" to "f".repeat(64),
            "requestId" to "changed-request",
            "dayId" to "2026-09-07",
            "mode" to "FREE",
            "difficulty" to "UNKNOWN",
            "authorUid" to "../foreign",
            "lessonId" to "",
        )) malformed(raw.patch(field, JsonPrimitive(value)))
    }

    @Test
    fun metadataNeverCoercesMissingFractionalStringBooleanOrUnsafeNumbers() {
        val raw = ticketFixture("recoveredSelected")
        for ((field, value) in listOf(
            "schemaVersion" to JsonPrimitive(2),
            "lessonVersion" to JsonPrimitive(0),
            "ratingVersion" to JsonPrimitive(-1),
            "ratingVersion" to JsonPrimitive(9_007_199_254_740_992L),
            "lifeCost" to JsonPrimitive(0),
            "lifeCost" to JsonPrimitive(0.5),
            "lifeCost" to JsonPrimitive(true),
            "lifeCost" to JsonNull,
            "priceNolics" to JsonPrimitive("10"),
            "priceNolics" to JsonPrimitive(0),
            "priceNolics" to JsonPrimitive(101),
            "issuedAtMs" to JsonPrimitive(-1),
        )) malformed(raw.patch(field, value))
    }

    @Test
    fun reservedHasNullTerminalAndUnknownStatesAreNeverDowngradedOrGivenFakeIssued() {
        val raw = ticketFixture("recoveredSelected")
        malformed(raw.patch("terminal", JsonObject(emptyMap())))
        for (status in listOf("ISSUED", "RETRY_REGEN", "UNKNOWN", "CANCELLED", "SETTLED")) {
            malformed(raw.patch("status", JsonPrimitive(status)))
        }
        malformedWire(JsonObject(mapOf("status" to JsonPrimitive("ISSUED"), "ticket" to raw)).toString())
        malformedWire(fixtures.getValue("recoveredSelected").toString())
    }

    @Test
    fun contextIsExplicitlyNullOrHasAllFourBoundedDocumentIds() {
        val raw = ticketFixture("recoveredSelected")
        assertNull(parse(raw.patch("contentContext", JsonNull)).metadata.contentContext)
        val context = raw.getValue("contentContext").jsonObject
        for (field in context.keys) {
            malformed(raw.patch("contentContext", JsonObject(context - field)))
            malformed(raw.patch("contentContext", context.patch(field, JsonPrimitive(""))))
        }
        malformed(raw.patch("contentContext", context.patch("questId", JsonPrimitive("x".repeat(1_501)))))
    }

    @Test
    fun unknownEnvelopeMetadataContextAndTerminalFieldsFailClosed() {
        val raw = ticketFixture("recoveredSelected")
        malformedWire(JsonObject(mapOf("ticket" to raw, "questions" to JsonArray(emptyList()))).toString())
        for (field in listOf("questions", "gradingKey", "requestHash", "balance", "completed", "futurePrivate")) {
            malformed(raw.patch(field, JsonPrimitive("secret")))
        }
        malformed(
            raw.patch(
                "contentContext",
                raw.getValue("contentContext").jsonObject
                    .patch("futurePrivate", JsonPrimitive("secret")),
            ),
        )
        for (name in listOf("recoveredCancelled", "recoveredSettled")) {
            malformed(patchTerminal(ticketFixture(name), "questions", JsonArray(emptyList())))
            malformed(patchTerminal(ticketFixture(name), "futurePrivate", JsonPrimitive("secret")))
        }
    }

    @Test
    fun cancellationRequiresExactReceiptIdentityTimestampAndFullHoldConservation() {
        val raw = ticketFixture("recoveredCancelled")
        val terminal = raw.getValue("terminal").jsonObject
        for (field in terminal.keys) malformed(raw.patch("terminal", JsonObject(terminal - field)))
        for (value in listOf(JsonNull, JsonObject(emptyMap()), JsonArray(emptyList()))) {
            malformed(raw.patch("terminal", value))
        }
        for ((field, value) in listOf(
            "receiptId" to JsonPrimitive("arena-cancel:" + "f".repeat(64)),
            "cancelledAtMs" to JsonPrimitive(1_788_695_999_999L),
            "releasedNolics" to JsonPrimitive(0),
            "releasedNolics" to JsonPrimitive("10"),
            "releasedLifePoints" to JsonPrimitive(0),
        )) malformed(patchTerminal(raw, field, value))
    }

    @Test
    fun settlementRequiresActualScoreIdentityTimeAndEveryFinancialTerm() {
        val raw = ticketFixture("recoveredSettled")
        val terminal = raw.getValue("terminal").jsonObject
        for (field in terminal.keys) malformed(raw.patch("terminal", JsonObject(terminal - field)))
        for ((field, value) in listOf(
            "receiptId" to JsonPrimitive("arena-cancel:" + raw.text("ticketId")),
            "attemptId" to JsonPrimitive("f".repeat(64)),
            "settledAtMs" to JsonPrimitive(1_788_695_999_999L),
            "percentScore" to JsonNull,
            "percentScore" to JsonPrimitive("75.5"),
            "percentScore" to JsonPrimitive(-1),
            "percentScore" to JsonPrimitive(101),
            "consumedNolics" to JsonPrimitive(0),
            "consumedLifePoints" to JsonPrimitive(0),
            "authorCreditNolics" to JsonPrimitive(-1),
            "authorCreditNolics" to JsonPrimitive(9),
            "sinkNolics" to JsonPrimitive(1.5),
            "royaltyReserveDeltaNolics" to JsonPrimitive(9_007_199_254_740_992L),
        )) malformed(patchTerminal(raw, field, value))
        malformedWire(envelope(raw).replace("\"percentScore\":75.5", "\"percentScore\":1e999"), request(raw))
    }

    @Test
    fun randomAllowsRealZeroPriceReceiptsButNeverAZeroSumAuthorReserveTransfer() {
        val random = ticketFixture("issuedRandom")
        assertEquals(0, parse(random).metadata.header.priceNolics)
        val settled =
            random.patch("status", JsonPrimitive("SETTLED"))
                .patch(
                    "terminal",
                    JsonObject(
                        mapOf(
                            "receiptId" to JsonPrimitive("arena-settle:" + random.text("ticketId")),
                            "attemptId" to random.getValue("attemptId"),
                            "settledAtMs" to random.getValue("issuedAtMs"),
                            "percentScore" to JsonPrimitive(0),
                            "consumedNolics" to JsonPrimitive(0),
                            "consumedLifePoints" to random.getValue("lifeCost"),
                            "authorCreditNolics" to JsonPrimitive(0),
                            "sinkNolics" to JsonPrimitive(0),
                            "royaltyReserveDeltaNolics" to JsonPrimitive(0),
                        ),
                    ),
                )
        val receipt = assertIs<ArenaTicketStatusObservation.Settled>(parse(settled)).receipt
        assertEquals(0L, receipt.authorCreditNolics)
        assertEquals(0L, receipt.sinkNolics)
        assertEquals(0L, receipt.royaltyReserveDeltaNolics)
        val transfer =
            patchTerminal(
                patchTerminal(settled, "authorCreditNolics", JsonPrimitive(1)),
                "royaltyReserveDeltaNolics",
                JsonPrimitive(-1),
            )
        malformed(transfer)
        val cancelled =
            random.patch("status", JsonPrimitive("CANCELLED"))
                .patch(
                    "terminal",
                    JsonObject(
                        mapOf(
                            "receiptId" to JsonPrimitive("arena-cancel:" + random.text("ticketId")),
                            "cancelledAtMs" to random.getValue("issuedAtMs"),
                            "releasedNolics" to JsonPrimitive(0),
                            "releasedLifePoints" to random.getValue("lifeCost"),
                        ),
                    ),
                )
        assertEquals(0, assertIs<ArenaTicketStatusObservation.Cancelled>(parse(cancelled)).receipt.releasedNolics)
    }

    @Test
    fun statusNeverInvokesQuestionParsingAndOldGetStillRequiresRealQuestions() {
        val neverQuestions =
            object : QuestionContentParser {
                override fun parse(payload: String): Result<QuestionContent> = error("Status must not parse questions")
            }
        val isolated = ArenaTicketWireParser(::sha, neverQuestions)
        val raw = ticketFixture("recoveredSelected")
        assertIs<ArenaTicketStatusObservation.Reserved>(isolated.status(envelope(raw), request(raw)))
        assertIs<ArenaEntryTicket.Reserved>(parser.get(fixtures.getValue("recoveredSelected").toString(), request(raw)))
        val missingQuestions = JsonObject(mapOf("status" to JsonPrimitive("ISSUED"), "ticket" to raw))
        assertFailsWith<ArenaRemoteFailure> { parser.get(missingQuestions.toString(), request(raw)) }
    }

    @Test
    fun malformedOrOversizedJsonNeverBecomesAnEmptyOrTerminalObservation() {
        for (wire in listOf("null", "[]", "true", "{", "{\"ticket\":{}}", " ".repeat(500_001))) malformedWire(wire)
    }

    @Test
    fun sealedObservationCannotClaimAStateDifferentFromItsMetadata() {
        val reserved = parse(ticketFixture("recoveredSelected")).metadata
        val cancelled = assertIs<ArenaTicketStatusObservation.Cancelled>(parse(ticketFixture("recoveredCancelled")))
        val settled = assertIs<ArenaTicketStatusObservation.Settled>(parse(ticketFixture("recoveredSettled")))
        assertFailsWith<IllegalArgumentException> {
            ArenaTicketStatusObservation.Cancelled(
                reserved,
                cancelled.receipt,
            )
        }
        assertFailsWith<IllegalArgumentException> { ArenaTicketStatusObservation.Settled(reserved, settled.receipt) }
        assertFailsWith<IllegalArgumentException> { ArenaTicketStatusObservation.Reserved(cancelled.metadata) }
    }

    // recovery.status's actual producer was checked against this exact question-free projection.
    // The immutable fixture is never modified; synthetic receipt variants above are negative/boundary tests.
    private fun ticketFixture(name: String): JsonObject =
        JsonObject(fixtures.getValue(name).jsonObject.getValue("ticket").jsonObject - setOf("questions", "requestHash"))

    private fun request(raw: JsonObject) =
        ArenaTicketGetRequest(
            "player",
            (raw["ticketId"] as? JsonPrimitive)?.content ?: ticketFixture("recoveredSelected").text("ticketId"),
        )

    private fun parse(raw: JsonObject) = parser.status(envelope(raw), request(raw))

    private fun envelope(raw: JsonObject) = JsonObject(mapOf("ticket" to raw)).toString()

    private fun JsonObject.text(field: String) = getValue(field).jsonPrimitive.content

    private fun JsonObject.patch(
        field: String,
        value: JsonElement,
    ) = JsonObject(this + (field to value))

    private fun patchTerminal(
        raw: JsonObject,
        field: String,
        value: JsonElement,
    ) = raw.patch("terminal", raw.getValue("terminal").jsonObject.patch(field, value))

    private fun malformed(
        raw: JsonObject,
        expected: ArenaTicketGetRequest = request(raw),
    ) {
        val failure = assertFailsWith<ArenaRemoteFailure> { parser.status(envelope(raw), expected) }
        assertEquals(ArenaRemoteFailureKind.MALFORMED_RESPONSE, failure.kind)
    }

    private fun malformedWire(
        wire: String,
        expected: ArenaTicketGetRequest = request(ticketFixture("recoveredSelected")),
    ) {
        val failure =
            assertFailsWith<ArenaRemoteFailure> {
                parser.status(wire, expected)
            }
        assertEquals(ArenaRemoteFailureKind.MALFORMED_RESPONSE, failure.kind)
    }
}

private fun sha(value: String): String =
    MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
