package com.tpov.schoolquiz.shared.feature.lesson_runner.data

import com.tpov.schoolquiz.shared.core.outbox.OutboxOperations
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.outbox.PrivateQuizSubmitOutboxWriter
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PrivateQuizSubmission
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Тело подачи приватной попытки на проводе (AD-6/AD-29, история 8.4): ключ — сам startId,
 * тело — ровно named-поля приёмника, повтор того же намерения даёт ту же строку.
 */
class PrivateQuizSubmitOutboxContractTest {

    private val submission = PrivateQuizSubmission(
        served = listOf(
            PrivateQuizSubmission.Served("q-1", 0),
            PrivateQuizSubmission.Served("q-2", 1),
        ),
        answers = listOf(
            PrivateQuizSubmission.Answer("q-1", 0, "{\"type\":\"single-choice\",\"selected\":\"opt-b\"}", 5_000),
            // Просроченный без ввода: показан, без ответа — поле ответа отсутствует явно.
            PrivateQuizSubmission.Answer("q-2", 1, null, 66_000),
        ),
    )

    @Test
    fun record_keysTheIntentByTheStartId() {
        val record = PrivateQuizSubmitOutboxWriter.record("owner-1", "cat-1", "quest-1", "start-000000001", submission, 1_000)
        assertEquals(
            OutboxOperations.mutationKey(OutboxOperations.SUBMIT_PRIVATE_QUIZ_ATTEMPT, "start-000000001"),
            record.mutationId,
            "ключ выводится из startId: повтор попытки — та же строка очереди (AD-6)",
        )
        assertEquals(OutboxOperations.SUBMIT_PRIVATE_QUIZ_ATTEMPT, record.operation)
        assertEquals("private_quiz:attempt:start-000000001", record.entityRef)
        assertEquals("owner-1", record.ownerUid)
    }

    @Test
    fun payload_carriesExactlyTheReceiverFields() {
        val record = PrivateQuizSubmitOutboxWriter.record("owner-1", "cat-1", "quest-1", "start-000000001", submission, 1_000)
        val payload = Json.parseToJsonElement(record.payload) as JsonObject
        assertEquals(setOf("ownerUid", "catalogId", "questId", "startId", "served", "answers"), payload.keys)

        assertEquals("start-000000001", payload["startId"]!!.jsonPrimitive.content)
        val served = payload["served"] as JsonArray
        assertEquals(2, served.size)
        assertEquals(0, served[0].let { (it as JsonObject)["codeAnswerIndex"]!!.jsonPrimitive.long }.toInt())
        val answers = payload["answers"] as JsonArray
        assertEquals(2, answers.size)
        val first = answers[0] as JsonObject
        assertEquals("{\"type\":\"single-choice\",\"selected\":\"opt-b\"}", first["answerPayload"]!!.jsonPrimitive.content)
        assertEquals(5_000L, first["answeredAtMs"]!!.jsonPrimitive.long)
        val second = answers[1] as JsonObject
        assertEquals(
            JsonNull,
            second["answerPayload"],
            "показан-без-ответа едет явной пустотой ответа, а не отсутствием позиции",
        )
    }

    @Test
    fun record_isDeterministic_perStartId() {
        val first = PrivateQuizSubmitOutboxWriter.record("owner-1", "cat-1", "quest-1", "start-000000001", submission, 1_000)
        val again = PrivateQuizSubmitOutboxWriter.record("owner-1", "cat-1", "quest-1", "start-000000001", submission, 9_999)
        assertEquals(first.mutationId, again.mutationId, "то же намерение — тот же ключ")
        assertEquals(first.payload, again.payload, "тело заморожено намерением, а не часами")
    }

    @Test
    fun theOperationIsAPermanentReceipt_withoutTermsConsent() {
        // 8.5: терминал хранится под startId (повтор безопасен), согласий у приватных операций нет.
        assertTrue(OutboxOperations.SUBMIT_PRIVATE_QUIZ_ATTEMPT in OutboxOperations.permanentReceiptOperations)
        assertTrue(OutboxOperations.SUBMIT_PRIVATE_QUIZ_ATTEMPT !in OutboxOperations.termsConsentOperations)
        val answer = assertNotNull(submission.answers.first())
        assertTrue(answer.answerPayload!!.contains("single-choice"))
    }
}
