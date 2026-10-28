package com.tpov.schoolquiz.shared.feature.quest_authoring.data

import com.tpov.schoolquiz.shared.feature.quest_authoring.data.mapper.QuestProposalsWireParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Строгий декодер страницы «Предложений» (история 4.25): чужой конверт страницу не строит.
 * Тела собираются JSON-литералами kotlinx — без ручного экранирования кавычек.
 */
class QuestProposalsWireParserTest {
    private fun rowJson(id: String, unreadable: Boolean = true): JsonObject =
        if (unreadable) {
            Json.parseToJsonElement("""{"unreadable":true,"proposalId":"$id"}""").jsonObject
        } else {
            Json.parseToJsonElement(
                """{"unreadable":false,"proposalId":"$id","kind":"REPORT","status":"CHECK_PASSED",
                    "stale":false,"targetLessonId":"l1","targetThemeId":null,"targetCatalogId":null,
                    "targetQuestId":null,"createdAtMs":10,"checkedAtMs":20,"decidedAtMs":0,
                    "feeNolics":0,"changedQuestions":0,"decision":null,"proposalText":"текст",
                    "proposalOwnVariant":null,"lessonTitle":null,"questionPreviews":[]}""",
            ).jsonObject
        }

    private fun envelope(
        rows: List<JsonObject>,
        view: String = QuestProposalsWireParser.VIEW_VERSION,
    ): Any = plain(
        Json.parseToJsonElement(
            """{"view":"$view","box":"INCOMING","rows":[${rows.joinToString(",") { it.toString() }}],
                "hasMore":false,"nextCursor":null}""",
        ),
    )

    /** Парсер ест «транспортные» значения: примитивы и карты, как их отдаёт Firebase. */
    private fun plain(element: kotlinx.serialization.json.JsonElement): Any = when (element) {
        is kotlinx.serialization.json.JsonObject -> element.entries.associate { (k, v) -> k to plain(v) }
        is kotlinx.serialization.json.JsonArray -> element.map { plain(it) }
        is kotlinx.serialization.json.JsonPrimitive ->
            element.content.toLongOrNull() ?: element.content.toBooleanStrictOrNull() ?: element.content
    }

    @Test
    fun `parses a well formed row with the report body`() {
        val result = QuestProposalsWireParser.questProposalsPage(envelope(listOf(rowJson("p1", unreadable = false))))
        val row = result.rows.single()
        assertEquals("p1", row.proposalId)
        assertEquals("REPORT", row.kind)
        assertEquals("CHECK_PASSED", row.status)
        assertEquals("текст", row.proposalText)
        assertEquals(false, row.stale)
        assertEquals(null, row.decision)
    }

    @Test
    fun `unreadable row marker passes through without invention`() {
        val result = QuestProposalsWireParser.questProposalsPage(envelope(listOf(rowJson("broken"))))
        val row = result.rows.single()
        assertTrue(row.unreadable)
    }

    @Test
    fun `foreign view version refuses the page`() {
        assertFailsWith<IllegalStateException> {
            QuestProposalsWireParser.questProposalsPage(envelope(emptyList(), view = "quest-proposals-v9"))
        }
    }

    @Test
    fun `row over the bound refuses the page`() {
        val many = (1..(QuestProposalsWireParser.MAX_ROWS + 1)).map { rowJson("p$it") }
        assertFailsWith<IllegalStateException> {
            QuestProposalsWireParser.questProposalsPage(envelope(many))
        }
    }
}
