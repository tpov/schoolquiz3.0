package com.tpov.schoolquiz.shared.feature.lesson_runner.data

import com.tpov.schoolquiz.shared.core.outbox.OutboxOperations
import com.tpov.schoolquiz.shared.core.outbox.OutboxPolicy
import com.tpov.schoolquiz.shared.core.outbox.OutboxRecord
import com.tpov.schoolquiz.shared.core.outbox.OutboxState
import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.outbox.ReadyAckOutboxCodec
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptIdentity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.longOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Клиентская половина одного онлайн-перехода next-question (история 2.6, ONLINE-O2 §4/§6):
 * имя операции `lesson_runner.READY_ACK`, тройка `(attemptId, questionIndex, windowId)`,
 * закодированная в sourceId, замороженное тело и правило retry без возрастного карантина.
 *
 * Серверная половина той же тройки — `MUTATION_HANDLERS["lesson_runner.READY_ACK"]` в
 * `functions/index.js` и Node-контрактные тесты рядом с ним; совпадение имён двух половин
 * сторожит `OutboxOperationsRegistryTest` в `:shared:core:outbox`.
 */
class ReadyAckOutboxContractTest {
    private val identity =
        AttemptIdentity(AttemptId("attempt-7"), "owner-1", LessonId("lesson-1"), lessonVersion = 3L, mode = Difficulty.HARD)

    // ── §6.1: ключ рождается вместе с намерением перехода и не меняется ─────────────────────

    @Test
    fun `mutation key is born from the transition triple and never varies`() {
        val first = ReadyAckOutboxCodec.mutationKey("attempt-7", 3, "win_1")
        repeat(3) { assertEquals(first, ReadyAckOutboxCodec.mutationKey("attempt-7", 3, "win_1")) }
        // Другой переход — другой ключ: чужая позиция, чужое окно и чужая попытка не дедуплицируются
        // в один эффект, и тот же переход не расходится на два ключа.
        val others =
            setOf(
                ReadyAckOutboxCodec.mutationKey("attempt-7", 4, "win_1"),
                ReadyAckOutboxCodec.mutationKey("attempt-7", 3, "win_2"),
                ReadyAckOutboxCodec.mutationKey("attempt-8", 3, "win_1"),
            )
        assertEquals(4, setOf(first, *others.toTypedArray()).size)
        assertTrue(others.none { it == first })
    }

    // ── §6.1/AD-6: ключ лежит в серверном charset, испорченная часть отвергается ─────────────

    @Test
    fun `sourceId carries the triple and the key stays inside the server charset`() {
        // Форма ровно та же, что закрепил образец контракта 2.5 (OnlineO2IdentityRetryContractTest):
        // attemptId кладётся как есть, без второго префикса.
        assertEquals("attempt-7-q3-window-1", ReadyAckOutboxCodec.sourceId("attempt-7", 3, "1"))
        val key = ReadyAckOutboxCodec.mutationKey("attempt-7", 3, "1")
        assertTrue(key.matches(Regex("[A-Za-z0-9_-]+")), "key must fit the server charset: $key")
        assertEquals("lesson_runner-READY_ACK-attempt-7-q3-window-1", key)
        // Составной windowId едет в ключ без перекодировки.
        assertEquals("attempt-7-q3-window-win_1", ReadyAckOutboxCodec.sourceId("attempt-7", 3, "win_1"))
        // Пустая или неподходящая часть отвергается, а не перекодируется в правдоподобный ключ.
        assertFailsWith<IllegalArgumentException> { ReadyAckOutboxCodec.sourceId(" ", 3, "win_1") }
        assertFailsWith<IllegalArgumentException> { ReadyAckOutboxCodec.sourceId("attempt-7", 3, " ") }
        assertFailsWith<IllegalArgumentException> { ReadyAckOutboxCodec.sourceId("attempt.7", 3, "win_1") }
        assertFailsWith<IllegalArgumentException> { ReadyAckOutboxCodec.sourceId("attempt-7", -1, "win_1") }
    }

    // ── §6.1/AD-2: тело замораживается в момент намерения, и часов клиента в нём нет ─────────

    @Test
    fun `row freezes the intent body without any client clock for the server`() {
        val createdAtMs = 1_800_000_000_000
        val row = ReadyAckOutboxCodec.buildReadyAckRow(identity, 3, "win_1", createdAtMs)

        assertEquals(OutboxOperations.READY_ACK, row.operation)
        assertEquals("owner-1", row.ownerUid)
        assertEquals(ReadyAckOutboxCodec.mutationKey("attempt-7", 3, "win_1"), row.mutationId)
        assertEquals("lesson_runner:ready_ack:attempt-7", row.entityRef)
        assertEquals(null, row.expectedVersion, "the window is created once and never versioned")

        val body = Json.parseToJsonElement(row.payload).let { assertIs<JsonObject>(it) }
        assertEquals(
            setOf("attemptId", "userId", "questionIndex", "windowId", "createdAtMs"),
            body.keys,
            "the intent body carries the triple and nothing else",
        )
        assertEquals("attempt-7", (body["attemptId"] as JsonPrimitive).content)
        assertEquals("owner-1", (body["userId"] as JsonPrimitive).content)
        assertEquals(3, (body["questionIndex"] as JsonPrimitive).int)
        assertEquals("win_1", (body["windowId"] as JsonPrimitive).content)
        assertEquals(createdAtMs, (body["createdAtMs"] as JsonPrimitive).longOrNull)
        // Сроки ставит серверная выдача: ни deadline, ни issuedAt клиент не предлагает (§5).
        assertTrue("answerDeadlineMs" !in body.keys && "issuedAtMs" !in body.keys)

        // Тот же переход, собранный снова (переигранная транзакция) — то же тело.
        val rebuilt = ReadyAckOutboxCodec.buildReadyAckRow(identity, 3, "win_1", createdAtMs)
        assertEquals(row.payload, rebuilt.payload)
    }

    // ── §6.1: правило retry — «повтор возвращает то же», без перезревания (§7) ───────────────

    @Test
    fun `ready ack is a permanent receipt operation and never ages into quarantine`() {
        assertTrue(OutboxOperations.READY_ACK in OutboxOperations.permanentReceiptOperations)
        val policy = OutboxPolicy()
        val foreverAgo = 0L
        val now = 365L * 86_400_000
        val record =
            OutboxRecord(
                1,
                ReadyAckOutboxCodec.mutationKey("attempt-7", 3, "win_1"),
                "owner-1",
                OutboxOperations.READY_ACK,
                "{}",
                OutboxState.WAITING,
                foreverAgo,
                attemptCount = 99,
                nextRetryAtMs = now + 1,
            )
        assertFalse(policy.isExpired(record, now), "aging must not kill the question before its own deadline")
        assertEquals(OutboxState.WAITING, policy.onFailure(record, SyncError.NoNetwork, now).state)
    }
}
