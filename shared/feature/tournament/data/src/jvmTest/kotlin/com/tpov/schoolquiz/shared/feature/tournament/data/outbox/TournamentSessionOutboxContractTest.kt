package com.tpov.schoolquiz.shared.feature.tournament.data.outbox

import com.tpov.schoolquiz.shared.core.outbox.MutationReceipt
import com.tpov.schoolquiz.shared.core.outbox.OutboxOperations
import com.tpov.schoolquiz.shared.feature.tournament.data.remote.TournamentEventWireParser
import com.tpov.schoolquiz.shared.feature.tournament.data.remote.TournamentMeetingWireParser
import com.tpov.schoolquiz.shared.feature.tournament.data.remote.TournamentSessionWireParser
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Клиентская половина контракта identity/retry для `tournament.SESSION_OP` (история 5.9):
 * ключ рождается с намерением и не меняется (AD-2), тело заморожено и несёт ровно allowlist
 * вызываемого, READY_ACK — постоянная квитанция, повтор ключа разбирается в тот же ответ.
 */
class TournamentSessionOutboxContractTest {
    private val sessionId = "q_1aac6a2caf58939d75a2296a426e8cc44dc55702da12cb330d554b025a086ae5"
    private val windowId = "win_aedbecedab4b"
    private val answerPayload = buildJsonObject { put("type", "single-choice"); put("selected", "a") }

    private val answer = TournamentSessionIntent.Answer(sessionId, 0, "q012", answerPayload)
    private val advance = TournamentSessionIntent.Advance(sessionId, 3)
    private val readyAck = TournamentSessionIntent.ReadyAck(sessionId, 1, windowId)

    @Test
    fun `mutation key is born with the intent and never varies`() {
        for (intent in listOf(answer, advance, readyAck)) {
            val first = TournamentSessionOutboxCodec.mutationKey(intent)
            repeat(3) { assertEquals(first, TournamentSessionOutboxCodec.mutationKey(intent)) }
        }
        // Другой слот — другой ключ; другая сессия — другой ключ.
        val otherSlot = TournamentSessionOutboxCodec.mutationKey(TournamentSessionIntent.ReadyAck(sessionId, 2, windowId))
        val otherSession = TournamentSessionOutboxCodec.mutationKey(TournamentSessionIntent.ReadyAck("m_" + "c".repeat(64), 1, windowId))
        assertTrue(otherSlot != TournamentSessionOutboxCodec.mutationKey(readyAck))
        assertTrue(otherSession != TournamentSessionOutboxCodec.mutationKey(readyAck))
    }

    @Test
    fun `key and derived operation id stay inside the server charset`() {
        for (intent in listOf(answer, advance, readyAck)) {
            val key = TournamentSessionOutboxCodec.mutationKey(intent)
            val operationId = TournamentSessionOutboxCodec.operationId(intent)
            assertTrue(key.matches(Regex("[A-Za-z0-9_-]+")), "key charset: $key")
            assertTrue(operationId.matches(Regex("[A-Za-z0-9_-]{16,128}")), "operationId charset: $operationId")
            assertTrue(!operationId.startsWith("ctw_") && !operationId.startsWith("freeze_advance_"))
            assertEquals(key, OutboxOperations.mutationKey(OutboxOperations.TOURNAMENT_SESSION_OP, TournamentSessionOutboxCodec.sourceId(intent)))
        }
        // Испорченный идентификатор отвергается, а не перекодируется в валидный с виду ключ (AD-6).
        assertFailsWith<IllegalArgumentException> {
            TournamentSessionOutboxCodec.sourceId(TournamentSessionIntent.Advance("bad/session", 0))
        }
        assertFailsWith<IllegalArgumentException> {
            TournamentSessionOutboxCodec.sourceId(TournamentSessionIntent.ReadyAck(sessionId, 1, "win dieb"))
        }
        assertFailsWith<IllegalArgumentException> {
            TournamentSessionOutboxCodec.sourceId(TournamentSessionIntent.Advance(sessionId, 20))
        }
    }

    @Test
    fun `ready ack is a permanent receipt operation and never ages to quarantine`() {
        assertTrue(OutboxOperations.TOURNAMENT_SESSION_OP in OutboxOperations.permanentReceiptOperations)
    }

    @Test
    fun `frozen payload carries exactly the callable allowlist per kind`() {
        val answerRow = TournamentSessionOutboxCodec.buildRow(answer, "player", 1788739200000L)
        assertEquals(OutboxOperations.TOURNAMENT_SESSION_OP, answerRow.operation)
        assertEquals("tournament:session:$sessionId", answerRow.entityRef)
        assertNull(answerRow.expectedVersion)
        val answerBody = Json.parseToJsonElement(answerRow.payload).jsonObject
        assertEquals(
            setOf("kind", "sessionId", "operationId", "questionIndex", "questionId", "answerPayload"),
            answerBody.keys,
        )
        assertEquals("ANSWER", answerBody.getValue("kind").jsonPrimitive.content)
        assertEquals("q012", answerBody.getValue("questionId").jsonPrimitive.content)
        assertEquals(answerPayload, answerBody.getValue("answerPayload"))
        // operationId в теле — тот же детерминированный, что у кодека.
        assertEquals(TournamentSessionOutboxCodec.operationId(answer), answerBody.getValue("operationId").jsonPrimitive.content)

        val advanceBody = Json.parseToJsonElement(TournamentSessionOutboxCodec.payload(advance).toString()).jsonObject
        assertEquals(setOf("kind", "sessionId", "operationId"), advanceBody.keys)
        assertEquals("ADVANCE", advanceBody.getValue("kind").jsonPrimitive.content)

        val ackBody = Json.parseToJsonElement(TournamentSessionOutboxCodec.payload(readyAck).toString()).jsonObject
        assertEquals(setOf("kind", "sessionId", "operationId", "questionIndex", "windowId"), ackBody.keys)
        assertEquals(windowId, ackBody.getValue("windowId").jsonPrimitive.content)
        assertEquals("READY_ACK", ackBody.getValue("kind").jsonPrimitive.content)
    }

    @Test
    fun `the same key may not silently carry a different frozen body`() {
        val first = TournamentSessionOutboxCodec.payload(answer).toString()
        val changed = TournamentSessionIntent.Answer(sessionId, 0, "q012", buildJsonObject { put("type", "single-choice"); put("selected", "b") })
        val second = TournamentSessionOutboxCodec.payload(changed).toString()
        assertEquals(TournamentSessionOutboxCodec.mutationKey(answer), TournamentSessionOutboxCodec.mutationKey(changed))
        assertTrue(first != second, "изменённое тело под тем же ключом обязан видеть репозиторий")
    }

    @Test
    fun `answer payload must fit the server storage budget`() {
        val oversized = buildJsonObject { put("type", "single-choice"); put("selected", "x".repeat(20_000)) }
        assertFailsWith<IllegalArgumentException> {
            TournamentSessionOutboxCodec.payload(TournamentSessionIntent.Answer(sessionId, 0, "q012", oversized))
        }
    }

    @Test
    fun `receipt parses only for the matching identity and keeps the replay flag`() {
        val parser = TournamentSessionReceiptParser(views = sessionParser())
        // В теле квитанции operationId — тот, что выведен кодеком (сервер отвечает присланным).
        val view = readFixture("readyAckView").toMutableMap().apply { put("operationId", JsonPrimitive(TournamentSessionOutboxCodec.operationId(readyAck))) }
        val envelope = buildJsonObject { put("replayed", true); put("result", JsonObject(view)) }
        val receipt =
            MutationReceipt(
                ownerUid = "player",
                mutationId = TournamentSessionOutboxCodec.mutationKey(readyAck),
                operation = OutboxOperations.TOURNAMENT_SESSION_OP,
                entityRef = TournamentSessionOutboxCodec.entityRef(sessionId),
                payloadJson = envelope.toString(),
                receivedAtMs = 1788739200000L,
            )
        val acknowledged =
            parser.parse(receipt, readyAck, ownerUid = "player") as TournamentSessionReceiptOutcome.Acknowledged
        assertTrue(acknowledged.receipt.replayed)
        assertEquals(windowId, acknowledged.receipt.readyAck?.windowId)
        assertEquals(1, acknowledged.receipt.readyAck?.questionIndex)

        // Чужой владелец, чужая операция, чужая ссылка или чужой ключ — квитанция не признаётся.
        for (broken in listOf(
            receipt.copy(ownerUid = "other"),
            receipt.copy(operation = OutboxOperations.READY_ACK),
            receipt.copy(entityRef = "tournament:session:other"),
            receipt.copy(mutationId = TournamentSessionOutboxCodec.mutationKey(advance)),
        )) {
            assertEquals(TournamentSessionReceiptOutcome.Invalid, parser.parse(broken, readyAck, ownerUid = "player"))
        }
        // ready-ACK квитанция с чужой тройкой (окно) — не признаётся.
        val otherWindow = TournamentSessionIntent.ReadyAck(sessionId, 1, "win_" + "f".repeat(12))
        assertEquals(TournamentSessionReceiptOutcome.Invalid, parser.parse(receipt, otherWindow, ownerUid = "player"))
    }

    @Test
    fun `answer receipt refuses an embedded ready ack and binds the operation id`() {
        val parser = TournamentSessionReceiptParser(views = sessionParser())
        val view = readFixture("answerView").toMutableMap().apply { put("operationId", JsonPrimitive(TournamentSessionOutboxCodec.operationId(answer))) }
        val envelope = buildJsonObject { put("result", JsonObject(view)) }
        val receipt =
            MutationReceipt(
                ownerUid = "player",
                mutationId = TournamentSessionOutboxCodec.mutationKey(answer),
                operation = OutboxOperations.TOURNAMENT_SESSION_OP,
                entityRef = TournamentSessionOutboxCodec.entityRef(sessionId),
                payloadJson = envelope.toString(),
                receivedAtMs = 1788739200000L,
            )
        val acknowledged = parser.parse(receipt, answer, ownerUid = "player") as TournamentSessionReceiptOutcome.Acknowledged
        assertTrue(!acknowledged.receipt.replayed)
        assertNull(acknowledged.receipt.readyAck)
        assertEquals("ANSWER_ACCEPTED", acknowledged.receipt.view.status)
        // operationId ответа — идентичность этого намерения; чужой operationId не признаётся.
        val otherAnswer = TournamentSessionIntent.Answer(sessionId, 5, "q012", answerPayload)
        assertEquals(TournamentSessionReceiptOutcome.Invalid, parser.parse(receipt, otherAnswer, ownerUid = "player"))
    }

    @Test
    fun `receipt status is pinned to what the server actually answers for the kind`() {
        val parser = TournamentSessionReceiptParser(views = sessionParser())
        fun receipt(
            intent: TournamentSessionIntent,
            status: String,
        ): MutationReceipt {
            val view =
                readFixture("answerView").toMutableMap().apply {
                    put("status", JsonPrimitive(status))
                    put("operationId", JsonPrimitive(TournamentSessionOutboxCodec.operationId(intent)))
                }
            return MutationReceipt(
                ownerUid = "player",
                mutationId = TournamentSessionOutboxCodec.mutationKey(intent),
                operation = OutboxOperations.TOURNAMENT_SESSION_OP,
                entityRef = TournamentSessionOutboxCodec.entityRef(intent.sessionId),
                payloadJson = buildJsonObject { put("result", JsonObject(view)) }.toString(),
                receivedAtMs = 1788739200000L,
            )
        }
        // ANSWER: ровно те статусы, что сервер отвечает ответу по `responseFor`.
        for (status in listOf("ANSWER_ACCEPTED", "QUESTION_TIMED_OUT", "COMPLETED")) {
            assertTrue(
                parser.parse(receipt(answer, status), answer, ownerUid = "player")
                    is TournamentSessionReceiptOutcome.Acknowledged,
                "ANSWER должен признавать $status",
            )
        }
        assertEquals(TournamentSessionReceiptOutcome.Invalid, parser.parse(receipt(answer, "CURRENT_QUESTION"), answer, ownerUid = "player"))
        assertEquals(TournamentSessionReceiptOutcome.Invalid, parser.parse(receipt(answer, "READY_ACK"), answer, ownerUid = "player"))
        // ADVANCE: свой набор; ответ чужого вида не признаётся.
        for (status in listOf("CURRENT_QUESTION", "QUESTION_TIMED_OUT", "COMPLETED")) {
            assertTrue(
                parser.parse(receipt(advance, status), advance, ownerUid = "player")
                    is TournamentSessionReceiptOutcome.Acknowledged,
                "ADVANCE должен признавать $status",
            )
        }
        assertEquals(TournamentSessionReceiptOutcome.Invalid, parser.parse(receipt(advance, "ANSWER_ACCEPTED"), advance, ownerUid = "player"))
    }

    @Test
    fun `server constants the codec hardcodes stay put`() {
        // Кодек печатает ключи и диапазон слотов под серверные константы `tournament-session.js`:
        // тихая смена сервера обязана валить этот тест, а не клиента в бою.
        val session = File(findRepo(), "functions/tournament-session.js").readText()
        assertTrue(session.contains("const QUESTION_COUNT = 20;"), "QUESTION_COUNT уже не 20 — MAX_QUESTIONS кодека отстал")
        assertTrue(session.contains("const MAX_ANSWER_BYTES = 16_384;"), "бюджет ответа уехал — MAX_ANSWER_BYTES кодека отстал")
        assertEquals(16_384, TournamentSessionOutboxCodec.MAX_ANSWER_BYTES)
    }

    private fun sessionParser() =
        TournamentSessionWireParser(events = TournamentEventWireParser(), meetings = TournamentMeetingWireParser())

    private fun readFixture(name: String): kotlinx.serialization.json.JsonObject {
        val path = "shared/feature/tournament/data/src/jvmTest/resources/tournament-client-fixtures.json"
        return Json.parseToJsonElement(File(findRepo(), path).readText()).jsonObject.getValue(name).jsonObject
    }

    private fun findRepo(): File =
        generateSequence(File(checkNotNull(System.getProperty("user.dir")))) { it.parentFile }
            .first { File(it, "settings.gradle.kts").isFile }
}
