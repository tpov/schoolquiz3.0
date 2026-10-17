package com.tpov.schoolquiz.shared.feature.tournament.data.outbox

import com.tpov.schoolquiz.shared.core.outbox.MutationReceipt
import com.tpov.schoolquiz.shared.core.outbox.OutboxOperations
import com.tpov.schoolquiz.shared.feature.tournament.data.remote.MalformedWire
import com.tpov.schoolquiz.shared.feature.tournament.data.remote.TournamentSessionWireParser
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentOperationView
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentReadyAckReceipt
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * Квитанция сессии соревнования, разобранная из сохранённого ответа приёмника: типизированный
 * вид `safeResponse` и флаг повтора ключа. Содержания ответа игрока здесь нет и быть не может —
 * сервер его не возвращает.
 */
data class TournamentSessionReceipt(
    val view: TournamentOperationView,
    val readyAck: TournamentReadyAckReceipt?,
    val replayed: Boolean,
)

/** Итог разбора сохранённой квитанции: разобрана или нет, третьего нет. */
sealed interface TournamentSessionReceiptOutcome {
    /** Квитанция подтверждена: identity сошлась, тело — по схеме. */
    data class Acknowledged(val receipt: TournamentSessionReceipt) : TournamentSessionReceiptOutcome

    /** Квитанция не признана: не признаётся и не читается, «неизвестно» не становится фактом. */
    data object Invalid : TournamentSessionReceiptOutcome
}

/**
 * Разбор сохранённой квитанции `tournament.SESSION_OP` — по образцу `ReadyAckReceiptParser`.
 *
 * Квитанция непрозрачна, пока identity не сошлась: владелец, операция, ссылка на сессию и ключ
 * намерения сверяются с ожидаемым intent до всякого чтения тела. Тело — конверт приёмника
 * `{replayed?, result}`: `result` — та же строгая проекция `safeResponse`, что и у прямого
 * вызова. Повтор ключа сервер отвечает той же квитанцией; флаг `replayed` читается, но на
 * разбор не влияет — байт-в-байт то же тело даёт тот же разбор.
 */
class TournamentSessionReceiptParser(
    private val views: TournamentSessionWireParser,
) {
    fun parse(
        receipt: MutationReceipt,
        expected: TournamentSessionIntent,
        ownerUid: String,
    ): TournamentSessionReceiptOutcome =
        runCatching { parseOrThrow(receipt, expected, ownerUid) }
            .getOrElse {
                    failure ->
                if (failure is InvalidReceipt) TournamentSessionReceiptOutcome.Invalid else throw failure
            }

    @Throws(InvalidReceipt::class)
    private fun parseOrThrow(
        receipt: MutationReceipt,
        expected: TournamentSessionIntent,
        ownerUid: String,
    ): TournamentSessionReceiptOutcome.Acknowledged {
        requireIdentity(receipt, expected, ownerUid)
        val envelope =
            runCatching {
                Json.parseToJsonElement(
                    receipt.payloadJson,
                ) as? JsonObject
            }.getOrNull() ?: invalid()
        envelope.withinKeys("replayed", "result")
        val result = envelope["result"] as? JsonObject ?: invalid()
        val replayed =
            when (val flag = envelope["replayed"]) {
                null -> false
                is JsonPrimitive -> flag.booleanOrNull ?: invalid()
                else -> invalid()
            }
        val view =
            try {
                views.parseOperationView(result.toString())
            } catch (_: MalformedWire) {
                invalid()
            }
        // operationId ответа, когда он есть, обязана быть идентичностью этого намерения — не чужим.
        view.operationId?.let { if (it != TournamentSessionOutboxCodec.operationId(expected)) invalid() }
        view.session?.let { if (it.sessionId != expected.sessionId) invalid() }
        // Статус пришпилен к тому, что сервер реально отвечает этому виду операции (`responseFor`
        // в functions/tournament-session.js): чужая квитанция не признаётся, а не читается мимо.
        val readyAck =
            when (expected) {
                is TournamentSessionIntent.ReadyAck -> {
                    if (view.status != "READY_ACK") invalid()
                    val ack = view.readyAck ?: invalid()
                    if (ack.questionIndex != expected.questionIndex || ack.windowId != expected.windowId) invalid()
                    ack
                }

                is TournamentSessionIntent.Answer -> {
                    if (view.readyAck != null) invalid()
                    if (view.status !in ANSWER_STATUSES) invalid()
                    null
                }

                is TournamentSessionIntent.Advance -> {
                    if (view.readyAck != null) invalid()
                    if (view.status !in ADVANCE_STATUSES) invalid()
                    null
                }
            }
        return TournamentSessionReceiptOutcome.Acknowledged(
            TournamentSessionReceipt(view = view, readyAck = readyAck, replayed = replayed),
        )
    }

    private fun requireIdentity(
        receipt: MutationReceipt,
        expected: TournamentSessionIntent,
        ownerUid: String,
    ) {
        if (receipt.ownerUid != ownerUid || receipt.operation != OutboxOperations.TOURNAMENT_SESSION_OP) invalid()
        if (receipt.entityRef != TournamentSessionOutboxCodec.entityRef(expected.sessionId)) invalid()
        if (receipt.mutationId != TournamentSessionOutboxCodec.mutationKey(expected)) invalid()
    }

    private fun kotlinx.serialization.json.JsonElement.withinKeys(vararg keys: String) {
        val allowed = keys.toSet()
        val self = this as? JsonObject ?: invalid()
        if (!self.keys.all { it in allowed }) invalid()
    }

    private fun invalid(): Nothing = throw InvalidReceipt()

    private class InvalidReceipt : Exception()

    private companion object {
        /** Ответ ANSWER по `responseFor`: принят, закрыт по краю вопроса или завершивший попытку. */
        val ANSWER_STATUSES = setOf("ANSWER_ACCEPTED", "QUESTION_TIMED_OUT", "COMPLETED")

        /** Ответ ADVANCE: вопрос ещё открыт, закрыт по краю или попытка завершена. */
        val ADVANCE_STATUSES = setOf("CURRENT_QUESTION", "QUESTION_TIMED_OUT", "COMPLETED")
    }
}
