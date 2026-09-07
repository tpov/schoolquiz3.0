@file:Suppress("MagicNumber") // Exact protocol fixture values, not production policy.

package com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote

import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.core.question_schema.QuestionContent
import com.tpov.schoolquiz.shared.core.question_schema.RedactedQuestionContent
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaCancelResponse
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaEntryMode
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaEntryTicket
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaPrepareRequest
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaPrepareResponse
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRemoteFailure
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRemoteFailureKind
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketCancelRequest
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketGetRequest
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketListRequest
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArenaTicketWireParserTest {
    private val fixtures =
        javaClass.classLoader!!.getResourceAsStream("arena-ticket-client-fixtures.json")!!
            .bufferedReader().use { Json.parseToJsonElement(it.readText()).jsonObject }
    private val parser = ArenaTicketWireParser(::sha256)
    private val selected = ArenaPrepareRequest.Selected("player", "client-selected", Difficulty.EASY, "lesson", 12)
    private val hard = ArenaPrepareRequest.Selected("player", "client-hard", Difficulty.HARD, "lesson", 10)

    @Test
    fun actualIssuerSelectedProjectionMatchesEveryPublicField() {
        val result = assertIs<ArenaPrepareResponse.Ticket>(parser.prepare(wire("issuedSelected"), selected))
        val ticket = assertIs<ArenaEntryTicket.Reserved>(result.ticket)
        val snapshot = ticket.snapshot
        val header = snapshot.header
        assertEquals(1, header.schemaVersion)
        assertEquals(rawTicket("issuedSelected").getValue("ticketId").jsonPrimitive.content, header.ticketId)
        assertEquals(rawTicket("issuedSelected").getValue("attemptId").jsonPrimitive.content, header.attemptId)
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
        assertEquals("author", snapshot.authorUid)
        assertEquals(5L, snapshot.ratingVersion)
        val context = requireNotNull(snapshot.contentContext)
        assertEquals("catalog", context.catalogId)
        assertEquals("quest", context.questId)
        assertEquals("section", context.sectionId)
        assertEquals("theme", context.themeId)
        val question = snapshot.questions.single()
        assertEquals("q1", question.questionId)
        assertEquals(2L, question.questionVersion)
        assertEquals(0, question.order)
        assertEquals("en", question.language)
        assertEquals(rawQuestion("issuedSelected").getValue("payload").jsonPrimitive.content, question.payload)
        val display = assertIs<QuestionContent.SingleChoice>(question.display)
        assertEquals("q1", display.id)
        assertEquals(Difficulty.EASY, display.difficulty)
        assertEquals("b", display.correctOptionId.raw)

        assertActualRuntimeNormalizesMissingSourceIds()
    }

    private fun assertActualRuntimeNormalizesMissingSourceIds() {
        // Unmodified actual runtime world: modern source documents have no embedded IDs.
        // Production normalization (not generator id injection) must make the frozen wire usable.
        val actual = fixtures.getValue("actualRuntimeMissingId").jsonObject
        assertEquals(JsonPrimitive(false), actual.getValue("sourceHadEmbeddedIds"))
        assertEquals(JsonPrimitive(true), actual.getValue("sourceQuestionsUnchanged"))
        actual.getValue("sourceQuestions").jsonArray.forEach { pair ->
            val source = pair.jsonArray[1].jsonObject.getValue("payload").jsonPrimitive.content
            assertTrue("id" !in Json.parseToJsonElement(source).jsonObject)
        }
        val actualTicket =
            assertIs<ArenaPrepareResponse.Ticket>(
                parser.prepare(
                    actual.getValue("issued").toString(),
                    ArenaPrepareRequest.Selected("player", "client-actual-missing-id", Difficulty.EASY, "lesson", 1),
                ),
            ).ticket
        assertEquals(setOf("EASY-0", "EASY-1"), actualTicket.snapshot.questions.map { it.questionId }.toSet())
        actualTicket.snapshot.questions.forEach { question ->
            val content = assertIs<QuestionContent.SingleChoice>(question.display)
            assertEquals(question.questionId, content.id)
            assertEquals("b", content.correctOptionId.raw)
        }
        assertEquals(
            JsonPrimitive(100),
            actual.getValue(
                "settled",
            ).jsonObject.getValue("receipts").jsonArray.single().jsonObject.getValue("percentScore"),
        )
    }

    @Test
    fun actualHardUsesRedactedDisplayAndRandomNeverHasAPrice() {
        val hardTicket = assertIs<ArenaPrepareResponse.Ticket>(parser.prepare(wire("issuedHard"), hard)).ticket
        assertIs<RedactedQuestionContent.SingleChoice>(hardTicket.snapshot.questions.single().display)
        val random = ArenaPrepareRequest.Random("player", "client-random", Difficulty.EASY)
        val randomTicket = assertIs<ArenaPrepareResponse.Ticket>(parser.prepare(wire("issuedRandom"), random)).ticket
        assertEquals(ArenaEntryMode.RANDOM, randomTicket.snapshot.header.mode)
        assertEquals(0, randomTicket.snapshot.header.priceNolics)
    }

    @Test
    fun actualRecoveryAndIssuanceUseExactlyTheSameFrozenQuestions() {
        val issued = assertIs<ArenaPrepareResponse.Ticket>(parser.prepare(wire("issuedSelected"), selected)).ticket
        assertEquals(issued, parser.get(wire("recoveredSelected"), getRequest("issuedSelected")))
        val unknown =
            JsonObject(
                rawTicket("recoveredSelected") +
                    mapOf(
                        "futurePrivate" to JsonPrimitive("secret"),
                        "bundleDigest" to JsonPrimitive("private"),
                    ),
            )
        val changed = JsonObject(fixtures.getValue("recoveredSelected").jsonObject + ("ticket" to unknown))
        assertEquals(issued, parser.get(changed.toString(), getRequest("issuedSelected")))
    }

    @Test
    fun cancelledAndSettledPrepareReplaysAreTerminalNotLaunchPermissions() {
        val cancelled =
            assertIs<ArenaEntryTicket.Cancelled>(
                assertIs<ArenaPrepareResponse.Ticket>(parser.prepare(wire("issuedCancelledReplay"), selected)).ticket,
            )
        assertEquals(ArenaTicketStatus.CANCELLED, cancelled.snapshot.header.status)
        assertEquals(10, cancelled.receipt.releasedNolics)
        assertEquals(50L, cancelled.receipt.releasedLifePoints)
        assertEquals("arena-cancel:${cancelled.snapshot.header.ticketId}", cancelled.receipt.receiptId)
        assertEquals(cancelled.snapshot.header.issuedAtMs, cancelled.receipt.cancelledAtMs)
        assertEquals(cancelled, parser.get(wire("recoveredCancelled"), getRequest("issuedSelected")))
        val settled = assertIs<ArenaEntryTicket.Settled>(parser.get(wire("recoveredSettled"), getRequest("issuedHard")))
        assertEquals(ArenaTicketStatus.SETTLED, settled.snapshot.header.status)
        assertEquals("arena-settle:${settled.snapshot.header.ticketId}", settled.receipt.receiptId)
        assertEquals(settled.snapshot.header.attemptId, settled.receipt.attemptId)
        assertEquals(settled.snapshot.header.issuedAtMs, settled.receipt.settledAtMs)
        assertEquals(75.5, settled.receipt.percentScore)
        assertEquals(10, settled.receipt.consumedNolics)
        assertEquals(50L, settled.receipt.consumedLifePoints)
        assertEquals(8L, settled.receipt.authorCreditNolics)
        assertEquals(2L, settled.receipt.sinkNolics)
        assertEquals(0L, settled.receipt.royaltyReserveDeltaNolics)
    }

    @Test
    fun cancelBindsRefundAndAlreadySettledReceiptToTheKnownTicket() {
        val selectedHeader = parser.get(wire("recoveredSelected"), getRequest("issuedSelected")).snapshot.header
        val cancel = ArenaTicketCancelRequest(selectedHeader, "cancel-client-selected")
        assertIs<ArenaCancelResponse.Cancelled>(parser.cancel(wire("cancelled"), cancel))
        val hardHeader = parser.get(wire("issuedHard"), getRequest("issuedHard")).snapshot.header
        assertIs<ArenaCancelResponse.AlreadySettled>(
            parser.cancel(wire("cancelSettled"), ArenaTicketCancelRequest(hardHeader, "cancel-client-hard")),
        )
        val retry =
            """{"status":"RETRY_REGEN",
               "ticketId":"${selectedHeader.ticketId}","requestId":"${cancel.requestId}"}"""
        assertEquals(
            ArenaCancelResponse.RetryRegeneration(selectedHeader.ticketId, cancel.requestId),
            parser.cancel(retry, cancel),
        )
        malformed { parser.cancel(wire("cancelSettled"), cancel) }
        val bad = change(fixtures.getValue("cancelled"), listOf("terminal", "releasedNolics"), JsonPrimitive(0))
        malformed { parser.cancel(bad.toString(), cancel) }
    }

    @Test
    fun regenerationPreservesTheOriginalRequestAndRejectsAReplacementId() {
        assertEquals(
            ArenaPrepareResponse.RetryRegeneration(selected.requestId),
            parser.prepare("""{"status":"RETRY_REGEN","requestId":"client-selected"}""", selected),
        )
        malformed { parser.prepare("""{"status":"RETRY_REGEN","requestId":"new-id"}""", selected) }
    }

    @Test
    fun actualRecoveryPagesUseStrictOrderingAndExclusiveCursorWithoutSkippingLookahead() {
        val first = parser.list(wire("listFirst"), ArenaTicketListRequest("player", pageSize = 2))
        assertEquals(2, first.tickets.size)
        assertEquals(first.tickets.last().ticketId, first.nextTicketId)
        val second = parser.list(wire("listLast"), ArenaTicketListRequest("player", first.nextTicketId, 2))
        assertEquals(1, second.tickets.size)
        assertNull(second.nextTicketId)
        assertTrue(second.tickets.single().ticketId > first.nextTicketId!!)
        assertEquals(3, (first.tickets + second.tickets).map { it.ticketId }.distinct().size)
        val rows = fixtures.getValue("listFirst").jsonObject.getValue("tickets").jsonArray
        val badOrder = change(fixtures.getValue("listFirst"), listOf("tickets"), JsonArray(rows.reversed()))
        malformed { parser.list(badOrder.toString(), ArenaTicketListRequest("player", pageSize = 2)) }
        malformed { parser.list(wire("listFirst"), ArenaTicketListRequest("player", pageSize = 1)) }
        malformed { parser.list(wire("listFirst"), ArenaTicketListRequest("player", first.nextTicketId, 2)) }
    }

    @Test
    fun missingAndNonFiniteOrStringNumbersNeverTurnIntoZero() {
        for ((field, value) in listOf(
            "schemaVersion" to JsonPrimitive(2), "lessonVersion" to JsonPrimitive(0),
            "lifeCost" to JsonPrimitive("50"), "priceNolics" to JsonPrimitive(0),
            "priceNolics" to JsonPrimitive(101), "ratingVersion" to JsonPrimitive(1.5),
            "issuedAtMs" to JsonPrimitive(9_007_199_254_740_992L), "dayId" to JsonPrimitive("2026-09-05"),
            "lifeCost" to JsonNull, "status" to JsonPrimitive("UNKNOWN"),
        )) {
            malformed { parser.prepare(changedTicket(field, value), selected) }
        }
        for (field in listOf("lifeCost", "priceNolics", "contentContext", "terminal", "questions")) {
            val ticket = JsonObject(rawTicket("issuedSelected") - field)
            val root = JsonObject(fixtures.getValue("issuedSelected").jsonObject + ("ticket" to ticket))
            malformed { parser.prepare(root.toString(), selected) }
        }
    }

    @Test
    fun cryptographicBindingsAndOwnerAndConfirmedPriceAreChecked() {
        for ((field, value) in listOf(
            "uid" to "other",
            "ticketId" to "a".repeat(64),
            "attemptId" to "b".repeat(64),
            "requestId" to "replaced",
            "lessonId" to "other",
        )) {
            malformed { parser.prepare(changedTicket(field, JsonPrimitive(value)), selected) }
        }
        malformed { parser.prepare(wire("issuedSelected"), selected.copy(maxPriceNolics = 9)) }
        malformed { parser.get(wire("recoveredSelected"), getRequest("issuedHard")) }
        malformed { parser.get(wire("recoveredSelected"), getRequest("issuedSelected").copy(ownerUid = "other")) }
        for ((input, expected) in fixtures.getValue("hashes").jsonObject) {
            assertEquals(expected.jsonPrimitive.content, sha256(input))
        }
    }

    @Test
    fun malformedModernQuestionsDoNotUseLegacyFallbackAndFrozenOrderIsRequired() {
        val payload =
            Json.parseToJsonElement(
                rawQuestion("issuedSelected").getValue("payload").jsonPrimitive.content,
            ).jsonObject
        for (body in listOf(
            JsonObject(payload - "id"),
            JsonObject(payload - "difficulty"),
            JsonObject(payload + ("id" to JsonPrimitive("wrong"))),
            JsonObject(payload + ("difficulty" to JsonPrimitive("HARD"))),
        )) {
            malformed { parser.prepare(changedQuestion("payload", JsonPrimitive(body.toString())), selected) }
        }
        for ((field, value) in listOf(
            "order" to JsonPrimitive(1),
            "version" to JsonPrimitive(3),
            "id" to JsonPrimitive("wrong"),
            "difficulty" to JsonPrimitive("HARD"),
            "payload" to JsonPrimitive("{}"),
        )) {
            malformed { parser.prepare(changedQuestion(field, value), selected) }
        }
        val rows = rawTicket("issuedSelected").getValue("questions").jsonArray
        malformed { parser.prepare(changedTicket("questions", JsonArray(listOf(rows[0], rows[0]))), selected) }
        malformed { parser.prepare(changedTicket("questions", JsonArray(emptyList())), selected) }
    }

    @Test
    fun terminalConservationAndStatusMustMatchWithoutScoreFallback() {
        for ((field, value) in listOf(
            "percentScore" to JsonNull,
            "percentScore" to JsonPrimitive("75.5"),
            "percentScore" to JsonPrimitive(100.1),
            "authorCreditNolics" to JsonPrimitive(9),
            "consumedLifePoints" to JsonPrimitive(0),
            "attemptId" to JsonPrimitive("b".repeat(64)),
            "settledAtMs" to JsonPrimitive(0),
        )) {
            val bad = change(fixtures.getValue("recoveredSettled"), listOf("ticket", "terminal", field), value)
            malformed { parser.get(bad.toString(), getRequest("issuedHard")) }
        }
        val bad = change(fixtures.getValue("recoveredSettled"), listOf("ticket", "status"), JsonPrimitive("RESERVED"))
        malformed { parser.get(bad.toString(), getRequest("issuedHard")) }
    }

    @Test
    fun everyActualHardPublicFormatKeepsItsValidFrozenBytes() {
        val cases = fixtures.getValue("hardPublicPayloads").jsonArray
        assertEquals(5, cases.size)
        for (case in cases) {
            val payload = case.jsonObject.getValue("payload").jsonPrimitive.content
            val result = parser.get(hardWire(payload), getRequest("issuedHard"))
            assertEquals(payload, result.snapshot.questions.single().payload)
        }
    }

    @Test
    fun unsafeActualAlreadyRedactedProducerPayloadsCannotReachTheClient() {
        val cases = fixtures.getValue("unsafeAlreadyRedactedPayloads").jsonArray
        assertEquals(10, cases.size)
        for (case in cases) {
            val body = case.jsonObject
            assertTrue(
                body.getValue("producerStatus").jsonPrimitive.content in setOf("already-redacted", "not-applicable"),
            )
            malformed { parser.get(hardWire(body.getValue("payload").jsonPrimitive.content), getRequest("issuedHard")) }
        }
    }

    @Test
    fun hardAllowListRejectsUnknownRootAndNestedFieldsAcrossEverySupportedType() {
        for (case in fixtures.getValue("hardPublicPayloads").jsonArray) {
            val body = Json.parseToJsonElement(case.jsonObject.getValue("payload").jsonPrimitive.content).jsonObject
            val rowField = listOf("options", "items", "candidates").single { it in body }
            for (field in listOf("futureKey", "correctOptionId", "info", "idMap", "correctCandidateId")) {
                val rows = body.getValue(rowField).jsonArray
                val unsafeRow = JsonObject(rows.first().jsonObject + (field to JsonPrimitive("secret")))
                val unsafeRows = JsonArray(listOf(unsafeRow) + rows.drop(1))
                val unsafe = JsonObject(body + (rowField to unsafeRows))
                malformed { parser.get(hardWire(unsafe.toString()), getRequest("issuedHard")) }
            }
            val unsafeRoot = JsonObject(body + ("futureBank" to JsonObject(mapOf("answer" to JsonPrimitive("b")))))
            malformed { parser.get(hardWire(unsafeRoot.toString()), getRequest("issuedHard")) }
        }
        val fill =
            fixtures.getValue("hardPublicPayloads").jsonArray.single {
                it.jsonObject.getValue("name").jsonPrimitive.content == "FILL_BLANK"
            }.jsonObject
        val body = Json.parseToJsonElement(fill.getValue("payload").jsonPrimitive.content).jsonObject
        val hiddenBlank =
            JsonObject(
                body + (
                    "blanks" to
                        JsonArray(
                            listOf(
                                JsonObject(mapOf("id" to JsonPrimitive("b1"), "answer" to JsonPrimitive("secret"))),
                            ),
                        )
                ),
            )
        malformed { parser.get(hardWire(hiddenBlank.toString()), getRequest("issuedHard")) }
        val hiddenSegment =
            JsonObject(
                body + (
                    "protectedTextSegments" to
                        JsonArray(
                            listOf(
                                JsonObject(mapOf("answer" to JsonPrimitive("secret"))),
                            ),
                        )
                ),
            )
        malformed { parser.get(hardWire(hiddenSegment.toString()), getRequest("issuedHard")) }
    }

    @Test
    fun duplicateMembersCannotHidePrivateRowsInTheRetainedHardBytes() {
        val payload = rawQuestion("issuedHard").getValue("payload").jsonPrimitive.content
        val duplicateRoot = payload.replace("\"options\":", "\"options\":[{\"futureKey\":\"secret\"}],\"options\":")
        malformed { parser.get(hardWire(duplicateRoot), getRequest("issuedHard")) }
        val escapedDuplicate =
            payload.replace(
                "\"options\":",
                "\"opt\\u0069ons\":[{\"answer\":\"secret\"}],\"options\":",
            )
        malformed { parser.get(hardWire(escapedDuplicate), getRequest("issuedHard")) }
        val duplicateNested = payload.replace("\"id\":\"a\"", "\"id\":\"private-id\",\"id\":\"a\"")
        malformed { parser.get(hardWire(duplicateNested), getRequest("issuedHard")) }
        // Ordinary punctuation and escaped quotes inside user-visible text are not object members.
        val body = Json.parseToJsonElement(payload).jsonObject
        val harmlessText = JsonObject(body + ("text" to JsonPrimitive("{\"key\":\"text\"}, brackets [] and slash \\")))
        val harmless = " \n" + harmlessText.toString() + "\n "
        assertEquals(
            harmless,
            parser.get(hardWire(harmless), getRequest("issuedHard")).snapshot.questions.single().payload,
        )
    }

    private fun hardWire(payload: String): String =
        change(
            fixtures.getValue("issuedHard"),
            listOf("ticket", "questions", "0", "publicQuestion", "payload"),
            JsonPrimitive(payload),
        ).toString()

    private fun wire(name: String): String = fixtures.getValue(name).toString()

    private fun rawTicket(name: String): JsonObject = fixtures.getValue(name).jsonObject.getValue("ticket").jsonObject

    private fun rawQuestion(name: String): JsonObject =
        rawTicket(name).getValue("questions").jsonArray.single().jsonObject.getValue("publicQuestion").jsonObject

    private fun getRequest(name: String): ArenaTicketGetRequest =
        ArenaTicketGetRequest("player", rawTicket(name).getValue("ticketId").jsonPrimitive.content)

    private fun changedTicket(
        field: String,
        value: JsonElement,
    ): String = change(fixtures.getValue("issuedSelected"), listOf("ticket", field), value).toString()

    private fun changedQuestion(
        field: String,
        value: JsonElement,
    ): String =
        change(
            fixtures.getValue("issuedSelected"),
            listOf("ticket", "questions", "0", "publicQuestion", field),
            value,
        ).toString()

    private fun change(
        node: JsonElement,
        path: List<String>,
        value: JsonElement,
    ): JsonElement {
        if (path.isEmpty()) return value
        val head = path.first()
        return when (node) {
            is JsonObject -> JsonObject(node + (head to change(node.getValue(head), path.drop(1), value)))
            is JsonArray ->
                JsonArray(
                    node.mapIndexed { index, child ->
                        if (index == head.toInt()) change(child, path.drop(1), value) else child
                    },
                )
            else -> error("Invalid fixture path")
        }
    }

    private fun malformed(block: () -> Unit) {
        assertEquals(ArenaRemoteFailureKind.MALFORMED_RESPONSE, assertFailsWith<ArenaRemoteFailure>(block = block).kind)
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") {
            (it.toInt() and 255).toString(16).padStart(2, '0')
        }
}
