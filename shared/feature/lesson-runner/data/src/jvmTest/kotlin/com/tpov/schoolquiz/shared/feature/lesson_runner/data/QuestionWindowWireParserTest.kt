@file:Suppress("MagicNumber")

package com.tpov.schoolquiz.shared.feature.lesson_runner.data

import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.QuestionWindowWireParser
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.QuestionDeliveryVerdict
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Проводные формы окна перехода пришиты к ответам сервера истории 2.6 (`functions/index.js`:
 * `beginLessonQuestionWindow` → `windowView`, `fetchNextLessonQuestion` → вердикт выдачи).
 * Форма — часть контракта ONLINE-O2: лишний, недостающий или испорченный ключ не читается молча.
 */
class QuestionWindowWireParserTest {
    private val parser = QuestionWindowWireParser()

    // ── вид окна: identity + захваченная конфигурация, ровно как отдаёт сервер ────────────────

    @Test
    fun windowViewParsesTheServerShapeAndCarriesTheCapturedConfig() {
        val wire =
            """
            {
              "schemaVersion": 1,
              "windowId": "win_1a2b3c4d5e6f",
              "attemptId": "attempt-7",
              "completedQuestionIndex": 2,
              "nextQuestionIndex": 3,
              "state": "OPEN",
              "windowSeconds": 60,
              "allocatedSeconds": 24,
              "openedAtMs": 1760000000000,
              "expiresAtMs": 1760000060000
            }
            """.trimIndent()
        val view = parser.parseWindowView(wire)
        assertEquals("win_1a2b3c4d5e6f", view.windowId)
        assertEquals("attempt-7", view.attemptId)
        assertEquals(2, view.completedQuestionIndex)
        assertEquals(3, view.nextQuestionIndex)
        assertEquals("OPEN", view.state)
        assertEquals(60, view.windowSeconds)
        assertEquals(24, view.allocatedSeconds)
        assertEquals(1_760_000_000_000, view.openedAtMs)
        assertEquals(1_760_000_060_000, view.expiresAtMs)
    }

    @Test
    fun windowViewRejectsMissingExtraOrCorruptKeys() {
        val base =
            """
            {
              "schemaVersion": 1,
              "windowId": "win_1",
              "attemptId": "attempt-7",
              "completedQuestionIndex": 2,
              "nextQuestionIndex": 3,
              "state": "OPEN",
              "windowSeconds": 60,
              "allocatedSeconds": 24,
              "openedAtMs": 10,
              "expiresAtMs": 70
            }
            """.trimIndent()
        assertEquals(10, parser.parseWindowView(base).openedAtMs)

        // Недостающее поле — «неизвестно», не ноль.
        val withoutExpires = Json.parseToJsonElement(base).let { assertIs<JsonObject>(it) }.toMutableMap()
        withoutExpires.remove("expiresAtMs")
        assertMalformed(withoutExpires.toWire())

        // Лишний ключ — дрейф формы, не читается молча.
        val withExtra = withoutExpires.toMutableMap()
        withExtra["bonus"] = JsonPrimitive(1)
        assertMalformed(withExtra.toWire())

        // Число вне безопасного диапазона и не-число — испорченный провод.
        assertMalformed(base.replace("\"openedAtMs\": 10", "\"openedAtMs\": 123456789012345678901234567890"))
        assertMalformed(base.replace("\"windowSeconds\": 60", "\"windowSeconds\": \"60\""))

        // Чужая schemaVersion — не эта поверхность.
        assertMalformed(base.replace("\"schemaVersion\": 1", "\"schemaVersion\": 2"))
        assertMalformed("[]")
    }

    // ── выдача: один и тот же факт при выдаче и при повторе, байт-в-байт как от сервера ───────

    @Test
    fun deliveredVerdictParsesTheServerShapeWithServerFactsOnly() {
        val wire =
            """
            {
              "status": "DELIVERED",
              "windowId": "win_1a2b3c4d5e6f",
              "questionIndex": 3,
              "questionId": "q4",
              "issuedAtMs": 1760000010000,
              "answerDeadlineMs": 1760000034000,
              "remainderMs": 22000,
              "serverNowMs": 1760000012000
            }
            """.trimIndent()
        val verdict = parser.parseDeliveryVerdict(wire)
        val delivered = assertIs<QuestionDeliveryVerdict.Delivered>(verdict)
        assertEquals("win_1a2b3c4d5e6f", delivered.windowId)
        assertEquals(3, delivered.delivery.questionIndex)
        assertEquals("q4", delivered.delivery.questionId)
        assertEquals(1_760_000_010_000, delivered.delivery.issuedAtMs)
        assertEquals(1_760_000_034_000, delivered.delivery.answerDeadlineMs)
        assertEquals(22_000, delivered.remainderMs)
        assertEquals(1_760_000_012_000, delivered.serverNowMs)
    }

    @Test
    fun expiredVerdictParsesAndKeepsUnknownWindowIdUnknown() {
        val wire =
            """
            {
              "status": "WINDOW_EXPIRED",
              "windowId": "win_1a2b3c4d5e6f",
              "closedAtMs": 1760000060000,
              "serverNowMs": 1760000060000
            }
            """.trimIndent()
        val expired = assertIs<QuestionDeliveryVerdict.WindowExpired>(parser.parseDeliveryVerdict(wire))
        assertEquals("win_1a2b3c4d5e6f", expired.windowId)

        // Сервер оставляет id пустым на испорченном документе: null остаётся null, не строкой.
        val anonymous = wire.replace("\"windowId\": \"win_1a2b3c4d5e6f\"", "\"windowId\": null")
        assertNull(assertIs<QuestionDeliveryVerdict.WindowExpired>(parser.parseDeliveryVerdict(anonymous)).windowId)
    }

    @Test
    fun unknownStatusAndBrokenDeliveryShapeAreMalformed() {
        assertMalformed("""{"status": "SOMETHING_ELSE", "serverNowMs": 1}""")
        assertMalformed(
            """
            {
              "status": "DELIVERED",
              "windowId": "win_1",
              "questionIndex": 3,
              "questionId": "q4",
              "issuedAtMs": 10,
              "answerDeadlineMs": 34,
              "serverNowMs": 12
            }
            """.trimIndent(),
        ) // remainderMs отсутствует
        // Выдача с deadline раньше момента выдачи противоречива и не разбирается в факт.
        assertMalformed(
            """
            {
              "status": "DELIVERED",
              "windowId": "win_1",
              "questionIndex": 3,
              "questionId": "q4",
              "issuedAtMs": 100,
              "answerDeadlineMs": 50,
              "remainderMs": 0,
              "serverNowMs": 100
            }
            """.trimIndent(),
        )
    }

    // ── повтор выдачи — то же тело, тот же разбор (§4/§7: байт-в-байт то же) ──────────────────

    @Test
    fun repeatOfTheSameDeliveryWireParsesToTheSameVerdict() {
        val wire =
            """
            {
              "status": "DELIVERED",
              "windowId": "win_x",
              "questionIndex": 1,
              "questionId": "q2",
              "issuedAtMs": 1000,
              "answerDeadlineMs": 25000,
              "remainderMs": 5000,
              "serverNowMs": 20000
            }
            """.trimIndent()
        assertTrue(
            parser.parseDeliveryVerdict(wire) == parser.parseDeliveryVerdict(wire),
            "the repeated wire is the same fact, not a new one",
        )
    }

    private fun assertMalformed(wire: String) {
        assertFailsWith<QuestionWindowWireParser.MalformedWindowWire> { parser.parseWindowView(wire) }
        assertFailsWith<QuestionWindowWireParser.MalformedWindowWire> { parser.parseDeliveryVerdict(wire) }
    }

    private fun MutableMap<String, kotlinx.serialization.json.JsonElement>.toWire(): String =
        JsonObject(this).toString()
}
