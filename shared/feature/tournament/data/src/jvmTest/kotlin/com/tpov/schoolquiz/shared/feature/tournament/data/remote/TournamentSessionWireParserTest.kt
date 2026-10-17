package com.tpov.schoolquiz.shared.feature.tournament.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Контракт разбора сессий против настоящих ответов контроллера (фикстуры сняты с
 * `functions/index.js`): окно чтения, settled-результат, ready-ACK, вопрос, записи скачивания
 * в обеих формах, ответ подготовки банка, дроп токена ссылки организатора, fail-closed.
 */
class TournamentSessionWireParserTest {
    private val fixture = loadFixture()
    private val views =
        TournamentSessionWireParser(events = TournamentEventWireParser(), meetings = TournamentMeetingWireParser())

    @Test
    fun `start operation view carries the session without window and with round number`() {
        val view = views.parseOperationView(fixture.wire("startView"))
        assertEquals(1L, view.schemaVersion)
        assertEquals("fixture_start_0000001", view.operationId)
        assertEquals("SESSION", view.status)
        assertEquals("q_1aac6a2caf58939d75a2296a426e8cc44dc55702da12cb330d554b025a086ae5", view.session?.sessionId)
        assertEquals("QUALIFICATION", view.session?.kind)
        assertEquals(1, view.session?.roundNumber)
        assertNull(view.session?.window)
        assertNull(view.session?.matchId)
        assertEquals(1788739221000L, view.session?.questionEndsAtMs)
    }

    @Test
    fun `read view carries authoritative window clocks and the served question`() {
        val view = views.parseOperationView(fixture.wire("readSessionStarted"))
        // У чтения сессии сервер не выводит ни schemaVersion, ни operationId, ни статус.
        assertNull(view.schemaVersion)
        assertNull(view.operationId)
        assertNull(view.status)
        assertEquals(false, view.deadlineReached)
        assertEquals(false, view.questionDeadlineReached)
        val session = requireNotNull(view.session)
        assertEquals(0, session.questionIndex)
        assertEquals("STARTED", session.status)
        val question = requireNotNull(view.currentQuestion)
        assertEquals("q012", question.questionId)
        assertEquals("SingleChoice", question.type)
        assertEquals(21000L, question.maxDurationMs)
        assertEquals("SingleChoiceRedacted", question.publicPayload.type)
        assertEquals("val", question.publicPayload.options?.get(1)?.text)
        assertNull(question.publicPayload.blanks)
        assertNull(question.publicPayload.candidates)
    }

    @Test
    fun `answer view carries the delivered window lastClosed and no answer material`() {
        val view = views.parseOperationView(fixture.wire("answerView"))
        assertEquals("ANSWER_ACCEPTED", view.status)
        val window = requireNotNull(view.window ?: view.session?.window)
        assertEquals("win_aedbecedab4b", window.windowId)
        assertEquals(0, window.completedQuestionIndex)
        assertEquals(1, window.nextQuestionIndex)
        assertEquals(1788739260000L, window.expiresAtMs)
        assertEquals("DELIVERED", window.state)
        assertEquals(0, view.lastClosed?.questionIndex)
        assertEquals("q012", view.lastClosed?.questionId)
        assertEquals("ANSWER", view.lastClosed?.kind)
        assertNull(view.readyAck)
        assertNull(view.result)
    }

    @Test
    fun `ready ack view carries the server receipt triple`() {
        val view = views.parseOperationView(fixture.wire("readyAckView"))
        assertEquals("READY_ACK", view.status)
        val ack = requireNotNull(view.readyAck)
        assertEquals(1L, ack.schemaVersion)
        assertEquals(1, ack.questionIndex)
        assertEquals("win_aedbecedab4b", ack.windowId)
        assertEquals("q032", ack.questionId)
        assertEquals(1788739200000L, ack.issuedAtMs)
        assertEquals(1788739221000L, ack.answerDeadlineMs)
        assertEquals(1788739200000L, ack.ackedAtMs)
    }

    @Test
    fun `settled read carries the result without any answer digits`() {
        val view = views.parseOperationView(fixture.wire("readSessionSettled"))
        assertEquals("SETTLED", view.session?.status)
        assertEquals(20, view.session?.questionIndex)
        assertNull(view.currentQuestion)
        val result = requireNotNull(view.result)
        assertEquals(100.0, result.percentScore)
        assertEquals(0L, result.elapsedMs)
        assertEquals(1788739200000L, result.completedAtMs)
    }

    @Test
    fun `current question may be an honest null`() {
        val view = views.parseOperationView(fixture.wire("readSessionSettled"))
        assertNull(view.currentQuestion)
    }

    @Test
    fun `download record decodes in both forms and keeps the replay flag`() {
        val first = views.parseDownloadRecord(fixture.wire("downloadFirst"))
        assertEquals("course", first.courseId)
        assertFalse(first.replayed)
        val replay = views.parseDownloadRecord(fixture.wire("downloadReplay"))
        assertEquals("course", replay.courseId)
        assertTrue(replay.replayed)
    }

    @Test
    fun `prepare reply carries the snapshot identity and composition`() {
        val reply = views.parsePrepareReply(fixture.wire("prepare"))
        assertEquals("bank_51c951b731585b5e6b916729e3bd8b17e1f2297c3edc979daa59ec765e5708e6", reply.snapshotId)
        assertEquals(60, reply.counts.easy)
        assertEquals(20, reply.counts.hard)
        assertEquals(80, reply.counts.total)
    }

    @Test
    fun `organizer admission link is accepted by key and decoded into nothing`() {
        val root = TournamentWireTestMutable.deepCopy(Json.parseToJsonElement(fixture.wire("startView")).jsonObject)
        root.put("admissionLink", mutableMapOf<String, Any?>("token" to "a".repeat(48)))
        val wire = Json.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), TournamentWireTestMutable.build(root))
        val view = views.parseOperationView(wire)
        // Токен не въезжает ни в один клиентский тип: у модели такого поля нет по построению.
        assertEquals("SESSION", view.status)
        assertTrue(view.toString().contains("token").not())
    }

    @Test
    fun `unknown keys and broken receipts fail closed`() {
        fun mutated(name: String, block: (MutableMap<String, Any?>) -> Unit): String {
            val copy = TournamentWireTestMutable.deepCopy(Json.parseToJsonElement(fixture.wire(name)).jsonObject)
            block(copy)
            return Json.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), TournamentWireTestMutable.build(copy))
        }
        // Лишний ключ ответа операции.
        assertFailsWith<MalformedWire> { views.parseOperationView(mutated("startView") { it.put("percentScore", 1) }) }
        // Член с дрейфом внутри окна.
        assertFailsWith<MalformedWire> {
            views.parseOperationView(
                mutated("readSessionAfterAck") {
                    val window = it["window"] as MutableMap<String, Any?>
                    window.put("questionId", "q032")
                },
            )
        }
        // Чужой ключ в публичной нагрузке вопроса.
        assertFailsWith<MalformedWire> {
            views.parseOperationView(
                mutated("readSessionStarted") {
                    val question = it["currentQuestion"] as MutableMap<String, Any?>
                    val payload = question["publicPayload"] as MutableMap<String, Any?>
                    payload.put("correctOptionId", "a")
                },
            )
        }
        // Скачивание: неизвестный третий ключ и испорченный флаг повтора.
        assertFailsWith<MalformedWire> {
            views.parseDownloadRecord(mutated("downloadFirst") { it.put("replayed", false) })
        }
        assertFailsWith<MalformedWire> {
            views.parseDownloadRecord(mutated("downloadReplay") { it.put("replayed", "yes") })
        }
    }

    @Test
    fun `optional scalars fail closed when present but wrongly typed`() {
        fun mutated(name: String, block: (MutableMap<String, Any?>) -> Unit): String {
            val copy = TournamentWireTestMutable.deepCopy(Json.parseToJsonElement(fixture.wire(name)).jsonObject)
            block(copy)
            return Json.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), TournamentWireTestMutable.build(copy))
        }
        // Необязательный булев, несущий строку, — «испорчено», а не «факт не назван».
        assertFailsWith<MalformedWire> {
            views.parseOperationView(mutated("readSessionStarted") { it.put("deadlineReached", "yes") })
        }
        // Отсутствующий статус чтения сессии — честная пустота; присутствующий пустой — испорчен.
        assertNull(views.parseOperationView(fixture.wire("readSessionStarted")).status)
        assertFailsWith<MalformedWire> { views.parseOperationView(mutated("startView") { it.put("status", "  ") }) }
        assertFailsWith<MalformedWire> { views.parseOperationView(mutated("startView") { it.put("status", null) }) }
    }

    private fun loadFixture(): kotlinx.serialization.json.JsonObject {
        val path = "shared/feature/tournament/data/src/jvmTest/resources/tournament-client-fixtures.json"
        val repo =
            generateSequence(File(checkNotNull(System.getProperty("user.dir")))) { it.parentFile }
                .first { File(it, "settings.gradle.kts").isFile }
        return Json.parseToJsonElement(File(repo, path).readText()).jsonObject
    }

    /** Сырой провод члена фикстуры — для парсера, а не для скалярного чтения. */
    private fun kotlinx.serialization.json.JsonObject.wire(key: String): String = getValue(key).toString()
}
