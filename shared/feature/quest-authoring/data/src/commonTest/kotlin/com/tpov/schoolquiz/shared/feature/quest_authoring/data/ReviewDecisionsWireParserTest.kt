package com.tpov.schoolquiz.shared.feature.quest_authoring.data

import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ReviewDecisionsRemoteDataSource
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ReviewDecisionsWireMark
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ReviewDecisionsWireRequest
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.reviewDecisionsWirePage
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewDecisionCursor
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewDecisionFilter
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewDecisionMarkValue
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewDecisionsAccessRefusedException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Строгий разбор вью «Решений» (4.17): версия пинится, границы честные, строка-метка unreadable
 * не притворяется данными, отметка декодируется по имени значения. Битое или расширенное тело —
 * отказ, а не выдуманная строка.
 */
class ReviewDecisionsWireParserTest {

    private fun page(vararg entries: String, hasMore: Boolean = false, cursor: String = "null") =
        """{"viewVersion":"quest-review-decisions-v1","entries":[${entries.joinToString()}],""" +
            """"hasMore":$hasMore,"nextCursor":$cursor}"""

    private fun row(json: String) = page(json)

    /** Firebase SDK отдаёт callable-ответ нативными коллекциями; тест декодирует так же. */
    private fun decoded(json: String): Any? {
        fun JsonElement.plain(): Any? = when (this) {
            is JsonNull -> null
            is JsonObject -> entries.associate { it.key to it.value.plain() }
            is JsonArray -> map { it.plain() }
            is JsonPrimitive -> when {
                isString -> content
                content == "true" -> true
                content == "false" -> false
                content.toLongOrNull() != null -> content.toLong()
                content.toDoubleOrNull() != null -> content.toDouble()
                else -> content
            }
        }
        return Json.parseToJsonElement(json).plain()
    }

    private val fullRow = """
        {"unreadable":false,"verdictId":"sub_l_WORDS_1","submissionId":"sub","questTitle":"Квест",
         "lessonId":"l","lessonTitle":"Урок","stage":"WORDS","attempt":1,"revision":3,
         "model":"glm-test","promptVersion":"words-v1","sources":["https://example.org/a"],
         "rationale":"основание","questionId":"q1","verdict":"FAIL","reason":"слово",
         "question":{"type":"SingleChoice","language":"ru","text":"Вопрос","payload":"{}"},
         "mark":{"mark":"INVALID","comment":"комментарий","adminUid":"admin","updatedAtMs":42}}
    """.replace("\n", "").replace(" ", "")

    @Test
    fun `decodes a full row with its mark`() {
        val page = reviewDecisionsWirePage(decoded(row(fullRow)))
        assertEquals(1, page.entries.size)
        val entry = page.entries.first()
        assertEquals(false, entry.unreadable)
        assertEquals("sub_l_WORDS_1", entry.verdictId)
        assertEquals("WORDS", entry.stage)
        assertEquals(3L, entry.revision)
        assertEquals("FAIL", entry.verdict)
        assertEquals(ReviewDecisionMarkValue.INVALID, entry.markValue)
        assertEquals("комментарий", entry.markComment)
        assertEquals(42L, entry.markUpdatedAtMs)
        assertEquals("Вопрос", entry.question!!.text)
        assertEquals(false, page.hasMore)
        assertEquals(null, page.nextCursor)
    }

    @Test
    fun `unreadable row keeps its trace without pretending data`() {
        val page = reviewDecisionsWirePage(
            decoded(
                page("""{"unreadable":true,"verdictId":"broken_verdict"}"""),
            ),
        )
        val entry = page.entries.single()
        assertTrue(entry.unreadable)
        assertEquals("broken_verdict", entry.verdictId)
        assertEquals("", entry.questionId)
        assertEquals(null, entry.verdict)
    }

    @Test
    fun `an unsupported view version is refused`() {
        val bad = page(fullRow).replace("quest-review-decisions-v1", "quest-review-decisions-v2")
        assertFailsWith<IllegalStateException> { reviewDecisionsWirePage(decoded(bad)) }
    }

    @Test
    fun `an unknown stage or verdict is refused`() {
        assertFailsWith<IllegalStateException> {
            reviewDecisionsWirePage(decoded(row(fullRow.replace("\"stage\":\"WORDS\"", "\"stage\":\"TRANSLATE\""))))
        }
        assertFailsWith<IllegalStateException> {
            reviewDecisionsWirePage(decoded(row(fullRow.replace("\"verdict\":\"FAIL\"", "\"verdict\":\"MAYBE\""))))
        }
    }

    @Test
    fun `over-bound page is refused instead of clipped`() {
        // 61 строки превышают серверную границу страницы (MAX_PAGE_ENTRIES = 60).
        val overBound = page(*Array(61) { fullRow })
        assertFailsWith<IllegalStateException> { reviewDecisionsWirePage(decoded(overBound)) }
    }

    @Test
    fun `cursor decodes losslessly`() {
        val page = reviewDecisionsWirePage(
            decoded(page(hasMore = true, cursor = """{"verdictId":"sub_l_WORDS_1","afterQuestionId":"q1"}""")),
        )
        assertTrue(page.hasMore)
        assertEquals(ReviewDecisionCursor("sub_l_WORDS_1", "q1"), page.nextCursor)
    }

    @Test
    fun `an unknown mark value is refused`() {
        assertFailsWith<IllegalStateException> {
            reviewDecisionsWirePage(decoded(row(fullRow.replace("\"mark\":\"INVALID\"", "\"mark\":\"MAYBE\""))))
        }
    }

    @Test
    fun `request carries the filter and cursor in the server shape`() {
        val request = ReviewDecisionsWireRequest(
            ReviewDecisionFilter.VALID,
            ReviewDecisionCursor("v1", "q1"),
        )
        assertEquals(ReviewDecisionFilter.VALID, request.filter)
        assertEquals("v1", request.cursor!!.verdictId)
    }

    @Test
    fun `access refused is a named exception type the repository lets through`() {
        val error = ReviewDecisionsAccessRefusedException()
        assertTrue(error is Exception)
        assertEquals("Decisions access refused by role", error.message)
    }

    @Test
    fun `wire mark decodes for the transport`() {
        val mark = ReviewDecisionsWireMark(ReviewDecisionMarkValue.VALID, "", 7)
        assertEquals(ReviewDecisionMarkValue.VALID, mark.value)
    }

    @Suppress("unused")
    private val sourceContract: ReviewDecisionsRemoteDataSource? = null
}
