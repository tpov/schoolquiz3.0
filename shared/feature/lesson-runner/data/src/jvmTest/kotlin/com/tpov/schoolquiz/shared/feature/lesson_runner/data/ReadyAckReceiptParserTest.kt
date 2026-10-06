package com.tpov.schoolquiz.shared.feature.lesson_runner.data

import com.tpov.schoolquiz.shared.core.outbox.MutationReceipt
import com.tpov.schoolquiz.shared.core.outbox.OutboxOperations
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.outbox.ReadyAckOutcome
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.outbox.ReadyAckOutboxCodec
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.outbox.ReadyAckReceiptParser
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptIdentity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Разбор готовой квитанции ready-ACK (ONLINE-O2 §4, история 2.6): клиент читает из неё серверные
 * факты выдачи — момент и deadline следующего вопроса — и ничего не додумывает. Искажённая или
 * чужая квитанция не читается вовсе: «неизвестно» не становится нулём и не становится стартом
 * часов (§5, замещающих часов нет).
 *
 * Сам ответ, который здесь разбирается, производится приёмником `submitMutation` на demo-backend
 * и доказан `lesson-ready-ack-handler.test.js` в `functions/`.
 */
class ReadyAckReceiptParserTest {
    private val parser = ReadyAckReceiptParser()
    private val identity =
        AttemptIdentity(AttemptId("attempt-7"), "owner-1", LessonId("lesson-1"), lessonVersion = 3L, mode = Difficulty.HARD)
    private val questionIndex = 3
    private val windowId = "win_abc123"

    private val issuedAtMs = 1_800_000_005_000L
    private val answerDeadlineMs = 1_800_000_029_000L

    /** Ровно тот ответ, который возвращает приёмник: `{replayed, result}`. */
    private fun responseJson(
        replayed: Boolean,
        overrides: Map<String, Any?> = emptyMap(),
    ): String =
        buildString {
            append("{\"replayed\":$replayed,\"result\":{\"schemaVersion\":1,\"status\":\"ACKED\",")
            append("\"attemptId\":\"attempt-7\",\"questionIndex\":3,\"windowId\":\"win_abc123\",")
            append("\"issuedAtMs\":$issuedAtMs,\"answerDeadlineMs\":$answerDeadlineMs,\"ackedAtMs\":1800000006000")
            for ((key, value) in overrides) {
                if (value == null) append(",\"$key\":null") else append(",\"$key\":$value")
            }
            append("}}")
        }

    private fun receipt(payloadJson: String): MutationReceipt =
        MutationReceipt(
            ownerUid = "owner-1",
            mutationId = ReadyAckOutboxCodec.mutationKey("attempt-7", questionIndex, windowId),
            operation = OutboxOperations.READY_ACK,
            entityRef = ReadyAckOutboxCodec.entityRef("attempt-7"),
            payloadJson = payloadJson,
            receivedAtMs = 1_800_000_007_000,
        )

    @Test
    fun `the acked receipt yields the server facts of the delivery`() {
        val outcome = parser.parse(receipt(responseJson(replayed = false)), identity, questionIndex, windowId)
        val acked = outcome as ReadyAckOutcome.Acked
        assertEquals("attempt-7", acked.receipt.attemptId)
        assertEquals(3, acked.receipt.questionIndex)
        assertEquals(windowId, acked.receipt.windowId)
        assertEquals(issuedAtMs, acked.receipt.issuedAtMs)
        assertEquals(answerDeadlineMs, acked.receipt.answerDeadlineMs)
        assertEquals(1_800_000_006_000, acked.receipt.ackedAtMs)
        assertEquals(false, acked.receipt.replayed)
    }

    @Test
    fun `the repeated key replays the same receipt and parses to the same outcome`() {
        // Сервер отвечает повтору той же квитанцией с replayed=true (§4) — разбор обязан совпасть.
        val first = parser.parse(receipt(responseJson(replayed = false)), identity, questionIndex, windowId)
        val replay = parser.parse(receipt(responseJson(replayed = true)), identity, questionIndex, windowId)
        val firstAcked = first as ReadyAckOutcome.Acked
        val replayAcked = replay as ReadyAckOutcome.Acked
        assertEquals(true, replayAcked.receipt.replayed)
        assertEquals(firstAcked.receipt.copy(replayed = true), replayAcked.receipt)
        assertEquals(answerDeadlineMs, replayAcked.receipt.answerDeadlineMs, "the deadline never restarts")
    }

    @Test
    fun `a receipt of another identity is not read at all`() {
        val wrongOwner = receipt(responseJson(false)).copy(ownerUid = "owner-2")
        val wrongOperation = receipt(responseJson(false)).copy(operation = OutboxOperations.SUBMIT_ATTEMPT)
        val wrongEntityRef = receipt(responseJson(false)).copy(entityRef = "lesson_runner:ready_ack:attempt-8")
        val wrongKey = receipt(responseJson(false)).copy(mutationId = ReadyAckOutboxCodec.mutationKey("attempt-7", 4, windowId))
        for (distorted in listOf(wrongOwner, wrongOperation, wrongEntityRef, wrongKey)) {
            assertEquals(
                ReadyAckOutcome.Invalid,
                parser.parse(distorted, identity, questionIndex, windowId),
                "an identity mismatch must not start the clock",
            )
        }
    }

    @Test
    fun `a body that does not match the expected triple is not read`() {
        val otherAttempt = parser.parse(receipt(responseJson(false, mapOf("attemptId" to "\"attempt-8\""))), identity, questionIndex, windowId)
        val otherQuestion = parser.parse(receipt(responseJson(false, mapOf("questionIndex" to "4"))), identity, questionIndex, windowId)
        val otherWindow = parser.parse(receipt(responseJson(false, mapOf("windowId" to "\"win_other\""))), identity, questionIndex, windowId)
        for (outcome in listOf(otherAttempt, otherQuestion, otherWindow)) {
            assertEquals(ReadyAckOutcome.Invalid, outcome)
        }
    }

    @Test
    fun `a missing or malformed clock field stays unknown instead of becoming zero`() {
        // Отсутствующий deadline — «неизвестно»; ноль был бы валидным серверным фактом, которого не было.
        val noDeadline = parser.parse(receipt(responseJson(false, mapOf("answerDeadlineMs" to null))), identity, questionIndex, windowId)
        assertEquals(ReadyAckOutcome.Invalid, noDeadline)
        val negativeDeadline = parser.parse(receipt(responseJson(false, mapOf("answerDeadlineMs" to "-5"))), identity, questionIndex, windowId)
        assertEquals(ReadyAckOutcome.Invalid, negativeDeadline)
        val noIssuedAt = parser.parse(receipt(responseJson(false, mapOf("issuedAtMs" to null))), identity, questionIndex, windowId)
        assertEquals(ReadyAckOutcome.Invalid, noIssuedAt)
    }

    @Test
    fun `a body of another shape is not read`() {
        val wrongStatus = parser.parse(receipt(responseJson(false, mapOf("status" to "\"PENDING\""))), identity, questionIndex, windowId)
        assertEquals(ReadyAckOutcome.Invalid, wrongStatus)
        val wrongSchema = parser.parse(receipt(responseJson(false, mapOf("schemaVersion" to "2"))), identity, questionIndex, windowId)
        assertEquals(ReadyAckOutcome.Invalid, wrongSchema)
        val notJson = parser.parse(receipt("not-json"), identity, questionIndex, windowId)
        assertEquals(ReadyAckOutcome.Invalid, notJson)
        val noResult = parser.parse(receipt("{\"replayed\":false}"), identity, questionIndex, windowId)
        assertEquals(ReadyAckOutcome.Invalid, noResult)
    }

    @Test
    fun `the same receipt bytes parse to the same outcome twice`() {
        val payloadJson = responseJson(replayed = true)
        val first = parser.parse(receipt(payloadJson), identity, questionIndex, windowId)
        val second = parser.parse(receipt(payloadJson), identity, questionIndex, windowId)
        assertEquals(first, second)
        assertTrue(first is ReadyAckOutcome.Acked)
    }
}
