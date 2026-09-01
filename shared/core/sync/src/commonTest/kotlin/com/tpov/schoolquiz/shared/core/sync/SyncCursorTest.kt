package com.tpov.schoolquiz.shared.core.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Курсор — пара, потому что одного времени мало.
 *
 * Две сущности, изменённые одним пакетом, получают одну миллисекунду. Курсор из одного числа
 * встаёт на неё, следующий проход просит «строго больше» — и вторая запись не читается никогда.
 * Аудит синхронизации отмечал это как риск; здесь он закрыт.
 */
class SyncCursorTest {

    @Test
    fun `given two changes in the same millisecond then the cursor still orders them`() {
        val first = SyncCursor(changedAtMs = 1_000L, docId = "aaa")
        val second = SyncCursor(changedAtMs = 1_000L, docId = "bbb")

        assertTrue(first < second, "при равном времени порядок задаёт id документа")
        assertTrue(second > first)
    }

    @Test
    fun `given different times then the time decides, whatever the ids are`() {
        val earlier = SyncCursor(changedAtMs = 1_000L, docId = "zzz")
        val later = SyncCursor(changedAtMs = 2_000L, docId = "aaa")

        assertTrue(earlier < later)
    }

    @Test
    fun `given the same pair then the cursors are equal`() {
        assertEquals(SyncCursor(5L, "id"), SyncCursor(5L, "id"))
        assertEquals(0, SyncCursor(5L, "id").compareTo(SyncCursor(5L, "id")))
    }

    @Test
    fun `given the beginning then it precedes everything`() {
        // Начало журнала — оно же результат принудительного сброса (AD-30).
        assertTrue(SyncCursor.BEGINNING < SyncCursor(1L, ""))
        assertTrue(SyncCursor.BEGINNING < SyncCursor(0L, "a"))
        assertEquals(0L, SyncCursor.BEGINNING.changedAtMs)
    }

    @Test
    fun `given a sorted journal then cursor order matches it`() {
        val journal =
            listOf(
                SyncCursor(2_000L, "b"),
                SyncCursor(1_000L, "b"),
                SyncCursor(1_000L, "a"),
                SyncCursor(2_000L, "a"),
            )

        assertEquals(
            listOf(
                SyncCursor(1_000L, "a"),
                SyncCursor(1_000L, "b"),
                SyncCursor(2_000L, "a"),
                SyncCursor(2_000L, "b"),
            ),
            journal.sorted(),
        )
    }

    @Test
    fun `given a full page then more is expected`() {
        val page = SyncChangePage(changes = List(SYNC_PAGE_SIZE) { it }, nextCursor = SyncCursor(1L, "x"), hasMore = true)

        assertTrue(page.hasMore)
        assertEquals(SYNC_PAGE_SIZE, page.changes.size)
    }

    @Test
    fun `given an empty page then there is nothing to move the cursor to`() {
        val page = SyncChangePage.empty<Int>()

        assertEquals(null, page.nextCursor, "пустая страница курсор не двигает")
        assertTrue(!page.hasMore)
    }
}
