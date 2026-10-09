package com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote

import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.QuestionDelivery
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.QuestionDeliveryVerdict
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.QuestionWindowView
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Строгий разбор проводных фактов окна перехода (ONLINE-O2 §3; формы — истории 2.6).
 *
 * Как у родственного разбора квитанции (`ReadyAckReceiptParser`): отсутствующее или испорченное
 * поле — «неизвестно», и никогда не подставляется нулём — ноль выглядел бы серверным фактом,
 * которого не было. Лишние и недостающие ключи ловятся одинаково: форма ответа — часть контракта,
 * дрейф формы не читается молча. Числа сервера — только в безопасном целочисленном диапазоне.
 */
class QuestionWindowWireParser {

    /** Разбор вида окна из `beginLessonQuestionWindow` (identity + захваченная конфигурация). */
    fun parseWindowView(wire: String): QuestionWindowView =
        fail { parseWindowViewOrThrow(wire) }

    /** Разбор вердикта `fetchNextLessonQuestion`: выдача или истёкшее окно (§3/§7). */
    fun parseDeliveryVerdict(wire: String): QuestionDeliveryVerdict =
        fail { parseDeliveryVerdictOrThrow(wire) }

    private fun parseWindowViewOrThrow(wire: String): QuestionWindowView {
        val root = objectValue(wire)
        root.exact(
            "schemaVersion",
            "windowId",
            "attemptId",
            "completedQuestionIndex",
            "nextQuestionIndex",
            "state",
            "windowSeconds",
            "allocatedSeconds",
            "openedAtMs",
            "expiresAtMs",
        )
        if (root.whole("schemaVersion") != WINDOW_SCHEMA_VERSION) invalid()
        return QuestionWindowView(
            windowId = root.requiredText("windowId"),
            attemptId = root.requiredText("attemptId"),
            completedQuestionIndex = root.requiredInt("completedQuestionIndex"),
            nextQuestionIndex = root.requiredInt("nextQuestionIndex"),
            state = root.requiredText("state"),
            windowSeconds = root.requiredInt("windowSeconds"),
            allocatedSeconds = root.requiredInt("allocatedSeconds"),
            openedAtMs = root.requiredWhole("openedAtMs"),
            expiresAtMs = root.requiredWhole("expiresAtMs"),
        )
    }

    private fun parseDeliveryVerdictOrThrow(wire: String): QuestionDeliveryVerdict {
        val root = objectValue(wire)
        when (root.requiredText("status")) {
            "DELIVERED" -> {
                root.exact(
                    "status",
                    "windowId",
                    "questionIndex",
                    "questionId",
                    "issuedAtMs",
                    "answerDeadlineMs",
                    "remainderMs",
                    "serverNowMs",
                )
                return QuestionDeliveryVerdict.Delivered(
                    windowId = root.requiredText("windowId"),
                    delivery =
                        QuestionDelivery(
                            questionIndex = root.requiredInt("questionIndex"),
                            questionId = root.requiredText("questionId"),
                            issuedAtMs = root.requiredWhole("issuedAtMs"),
                            answerDeadlineMs = root.requiredWhole("answerDeadlineMs"),
                        ),
                    remainderMs = root.requiredWhole("remainderMs"),
                    serverNowMs = root.requiredWhole("serverNowMs"),
                )
            }
            "WINDOW_EXPIRED" -> {
                root.exact("status", "windowId", "closedAtMs", "serverNowMs")
                return QuestionDeliveryVerdict.WindowExpired(
                    // Сервер оставляет id пустым на испорченном документе: «неизвестно», не ноль.
                    windowId = root.text("windowId"),
                    closedAtMs = root.requiredWhole("closedAtMs"),
                    serverNowMs = root.requiredWhole("serverNowMs"),
                )
            }
            else -> invalid()
        }
    }

    private fun objectValue(wire: String): JsonObject =
        Json.parseToJsonElement(wire) as? JsonObject ?: invalid()

    private fun JsonObject.exact(vararg keys: String) {
        val expected = keys.toSet()
        if (keys.size != expected.size || this.keys != expected) invalid()
    }

    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.int(key: String): Int? =
        (this[key] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull

    private fun JsonObject.whole(key: String): Long? =
        (this[key] as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull
            ?.takeIf { it in 0..MAX_SAFE_SERVER_INTEGER }

    private fun JsonObject.requiredText(key: String): String = text(key)?.takeIf { it.isNotBlank() } ?: invalid()

    private fun JsonObject.requiredInt(key: String): Int = int(key) ?: invalid()

    private fun JsonObject.requiredWhole(key: String): Long = whole(key) ?: invalid()

    private fun invalid(): Nothing = throw MalformedWindowWire()

    private inline fun <T> fail(block: () -> T): T =
        runCatching { block() }.getOrElse { failure ->
            if (failure is MalformedWindowWire) throw failure else throw MalformedWindowWire()
        }

    /** Маркер испорченной проводной формы; границы транспорта переводят его в типовой отказ. */
    class MalformedWindowWire : Exception()

    private companion object {
        const val WINDOW_SCHEMA_VERSION = 1L
        const val MAX_SAFE_SERVER_INTEGER = 9_007_199_254_740_991L
    }
}
