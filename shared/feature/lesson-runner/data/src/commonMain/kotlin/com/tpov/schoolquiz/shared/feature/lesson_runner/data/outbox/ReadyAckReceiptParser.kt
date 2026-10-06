package com.tpov.schoolquiz.shared.feature.lesson_runner.data.outbox

import com.tpov.schoolquiz.shared.core.outbox.MutationReceipt
import com.tpov.schoolquiz.shared.core.outbox.OutboxOperations
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptIdentity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/** Квитанция готовности, разобранная из ответа сервера: серверные факты выдачи, ничего клиентского. */
data class ReadyAckReceipt(
    val attemptId: String,
    val questionIndex: Int,
    val windowId: String,
    val issuedAtMs: Long,
    val answerDeadlineMs: Long,
    val ackedAtMs: Long,
    val replayed: Boolean,
)

/** Итог разбора готовой квитанции: разобрана или нет, третьего нет. */
sealed interface ReadyAckOutcome {
    /** Квитанция подтверждена; часы следующего вопроса — [ReadyAckReceipt.answerDeadlineMs] сервера. */
    data class Acked(val receipt: ReadyAckReceipt) : ReadyAckOutcome

    /** Квитанция не признана: не признаётся и не читается, «неизвестно» не становится нулём. */
    data object Invalid : ReadyAckOutcome
}

/**
 * Разбор готовой квитанции ready-ACK (ONLINE-O2 §4, история 2.6).
 *
 * Форма — та же, что у разбора расчёта попытки: квитанция непрозрачна, пока identity не сошлась
 * (владелец, операция, ссылка, ключ), а тело — по схеме и против ожидаемой тройки `(attemptId,
 * questionIndex, windowId)`. Поля времени обязательны: отсутствие означает «неизвестно» и никогда
 * не подставляется нулём — ноль выглядел бы валидным серверным фактом, которого не было. Повтор
 * ключа сервер отвечает той же квитанцией (§4), поэтому `replayed` читается, но на разбор не
 * влияет: байт-в-байт то же тело даёт тот же разбор.
 *
 * Ответы в рамках активного вопроса этот разбор не подменяет — расчёт попытки читает свой
 * `AttemptSettlementReceiptParser`; здесь только пер-вопросная квитанция перехода (§4).
 */
class ReadyAckReceiptParser {

    fun parse(
        receipt: MutationReceipt,
        expected: AttemptIdentity,
        questionIndex: Int,
        windowId: String,
    ): ReadyAckOutcome =
        runCatching { parseOrThrow(receipt, expected, questionIndex, windowId) }
            .getOrElse { failure -> if (failure is InvalidReceipt) ReadyAckOutcome.Invalid else throw failure }

    @Throws(InvalidReceipt::class)
    private fun parseOrThrow(
        receipt: MutationReceipt,
        expected: AttemptIdentity,
        questionIndex: Int,
        windowId: String,
    ): ReadyAckOutcome.Acked {
        requireIdentity(receipt, expected, questionIndex, windowId)
        val envelope =
            runCatching { Json.parseToJsonElement(receipt.payloadJson) as? JsonObject }.getOrNull()
                ?: invalid()
        val result = envelope["result"] as? JsonObject ?: invalid()
        if (result.whole("schemaVersion") != READY_ACK_SCHEMA_VERSION) invalid()
        if (result.text("status") != "ACKED") invalid()
        if (result.text("attemptId") != expected.id.value) invalid()
        if (result.int("questionIndex") != questionIndex) invalid()
        if (result.text("windowId") != windowId) invalid()
        return ReadyAckOutcome.Acked(
            ReadyAckReceipt(
                attemptId = result.requiredText("attemptId"),
                questionIndex = result.requiredInt("questionIndex"),
                windowId = result.requiredText("windowId"),
                issuedAtMs = result.requiredWhole("issuedAtMs"),
                answerDeadlineMs = result.requiredWhole("answerDeadlineMs"),
                ackedAtMs = result.requiredWhole("ackedAtMs"),
                replayed = envelope.booleanOrFalse("replayed"),
            ),
        )
    }

    private fun requireIdentity(
        receipt: MutationReceipt,
        expected: AttemptIdentity,
        questionIndex: Int,
        windowId: String,
    ) {
        if (receipt.ownerUid != expected.userId || receipt.operation != OutboxOperations.READY_ACK) invalid()
        if (receipt.entityRef != ReadyAckOutboxCodec.entityRef(expected.id.value)) invalid()
        if (receipt.mutationId != ReadyAckOutboxCodec.mutationKey(expected.id.value, questionIndex, windowId)) invalid()
    }

    private fun invalid(): Nothing = throw InvalidReceipt()

    private class InvalidReceipt : Exception()

    private fun JsonObject.text(key: String): String? =
        (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.int(key: String): Int? =
        (get(key) as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull

    private fun JsonObject.whole(key: String): Long? =
        (get(key) as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull
            ?.takeIf { it in 0..MAX_SAFE_SERVER_INTEGER }

    private fun JsonObject.requiredText(key: String): String = text(key)?.takeIf { it.isNotBlank() } ?: invalid()

    private fun JsonObject.requiredInt(key: String): Int = int(key) ?: invalid()

    private fun JsonObject.requiredWhole(key: String): Long = whole(key) ?: invalid()

    private fun JsonObject.booleanOrFalse(key: String): Boolean =
        (get(key) as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull ?: false

    private companion object {
        const val READY_ACK_SCHEMA_VERSION = 1L
        const val MAX_SAFE_SERVER_INTEGER = 9_007_199_254_740_991L
    }
}
