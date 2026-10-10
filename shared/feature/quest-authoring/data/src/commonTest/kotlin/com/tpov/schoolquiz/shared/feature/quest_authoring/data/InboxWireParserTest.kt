package com.tpov.schoolquiz.shared.feature.quest_authoring.data

import com.tpov.schoolquiz.shared.feature.quest_authoring.data.mapper.InboxWireParser
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.InboxAnchor
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.InboxItem
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.InboxReaders
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Строгий разбор view-конверта `inbox-views-v1`: чужая версия и кривая форма — отказ чтения,
 * читатели-маркеры обязаны соответствовать виду записи.
 */
class InboxWireParserTest {
    private fun page(vararg entries: Map<String, Any?>, nextAnchor: Map<String, Any?>? = null): Map<String, Any?> =
        buildMap {
            put("viewVersion", InboxWireParser.VIEW_VERSION)
            put("entries", entries.toList())
            nextAnchor?.let { put("nextAnchor", it) }
        }

    @Test
    fun `parses both entry kinds with their readers and lesson links`() {
        val result =
            InboxWireParser.inboxPage(
                page(
                    mapOf(
                        "kind" to "private_error_report", "id" to "r1", "lessonId" to "les-9",
                        "text" to "whole-lesson text", "createdAtMs" to 5_000L, "readers" to "author_only",
                    ),
                    mapOf(
                        "kind" to "lesson_comment", "id" to "c1", "lessonId" to "les-9",
                        "text" to "public comment", "createdAtMs" to 4_000L,
                        "readers" to "public", "authorNickname" to "Player",
                    ),
                ),
            )

        assertEquals(2, result.entries.size)
        val report = result.entries[0] as InboxItem.PrivateErrorReport
        assertEquals(InboxReaders.AUTHOR_ONLY, report.readers)
        assertEquals("les-9", report.lessonId)
        assertEquals("whole-lesson text", report.text)
        val comment = result.entries[1] as InboxItem.LessonComment
        assertEquals(InboxReaders.PUBLIC, comment.readers)
        assertEquals("Player", comment.authorNickname)
        assertNull(result.nextAnchor)
    }

    @Test
    fun `continuation anchor survives the parse`() {
        val result =
            InboxWireParser.inboxPage(
                page(nextAnchor = mapOf("atMs" to 1_234L, "id" to "anchor-id")),
            )

        assertEquals(InboxAnchor(atMs = 1_234L, id = "anchor-id"), result.nextAnchor)
    }

    @Test
    fun `foreign view version refuses the read`() {
        assertFailsWith<IllegalStateException> {
            InboxWireParser.inboxPage(mapOf("viewVersion" to "inbox-views-v2", "entries" to emptyList<Any?>()))
        }
    }

    @Test
    fun `readers marker must match the entry kind`() {
        assertFailsWith<IllegalStateException> {
            InboxWireParser.inboxPage(
                page(
                    mapOf(
                        "kind" to "private_error_report", "id" to "r1", "lessonId" to "les-9",
                        "text" to "t", "createdAtMs" to 1L, "readers" to "public",
                    ),
                ),
            )
        }
        assertFailsWith<IllegalStateException> {
            InboxWireParser.inboxPage(
                page(
                    mapOf(
                        "kind" to "lesson_comment", "id" to "c1", "lessonId" to "les-9",
                        "text" to "t", "createdAtMs" to 1L, "readers" to "author_only",
                    ),
                ),
            )
        }
    }

    @Test
    fun `missing mandatory field refuses the read and numeric coercion goes through Number`() {
        assertFailsWith<IllegalStateException> {
            InboxWireParser.inboxPage(
                page(
                    mapOf(
                        "kind" to "private_error_report", "id" to "r1", "lessonId" to "les-9",
                        "text" to "t", "readers" to "author_only",
                    ),
                ),
            )
        }
        val coerced = InboxWireParser.inboxPage(
            page(
                mapOf(
                    "kind" to "private_error_report", "id" to "r1", "lessonId" to "les-9",
                    "text" to "t", "createdAtMs" to 7, "readers" to "author_only",
                ),
            ),
        )
        assertTrue(coerced.entries.single().createdAtMs == 7L)
    }
}
