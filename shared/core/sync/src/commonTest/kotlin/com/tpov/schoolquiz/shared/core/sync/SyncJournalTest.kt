package com.tpov.schoolquiz.shared.core.sync

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Один цикл чтения на четыре журнала (AD-31): страница за страницей, курсор — после применения.
 */
class SyncJournalTest {

    private data class Doc(val cursor: SyncCursor, val value: String?)

    /** Журнал в памяти: отдаёт страницу строго после курсора по паре, как это делает Firestore. */
    private class Journal(private val docs: List<Doc>, private val pageSize: Int) {
        val requests = mutableListOf<SyncCursor>()

        fun page(cursor: SyncCursor): SyncChangePage<String> {
            requests += cursor
            val slice = docs.sortedBy { it.cursor }.filter { it.cursor > cursor }.take(pageSize)
            return journalPage(slice, pageSize, { it.cursor }, { it.value })
        }
    }

    private fun doc(ms: Long, id: String, value: String? = id) = Doc(SyncCursor(ms, id), value)

    @Test
    fun `given more records than a page then every page is applied and the cursor follows each`() = runTest {
        val state = InMemorySyncStateRepository()
        val journal = Journal(listOf(doc(1L, "a"), doc(2L, "b"), doc(3L, "c")), pageSize = 2)
        val applied = mutableListOf<List<String>>()
        val cursorAfterApply = mutableListOf<SyncCursor>()

        val result = state.syncJournal(
            cursorId = "j",
            fetch = journal::page,
            apply = { applied += it; cursorAfterApply += state.getCursor("j"); Result.success(Unit) },
        )

        assertEquals(Result.success(0), result)
        assertEquals(listOf(listOf("a", "b"), listOf("c")), applied)
        // Курсор после каждой применённой страницы равен паре последней записи этой страницы.
        assertEquals(SyncCursor(3L, "c"), state.getCursor("j"))
        assertEquals(listOf(SyncCursor.BEGINNING, SyncCursor(2L, "b")), journal.requests)
        // Двигается только ПОСЛЕ применения: в момент применения второй страницы курсор ещё на первой.
        assertEquals(listOf(SyncCursor.BEGINNING, SyncCursor(2L, "b")), cursorAfterApply)
    }

    @Test
    fun `given the middle of a page fails then the cursor stays before it and the page is reread`() = runTest {
        val state = InMemorySyncStateRepository()
        state.setCursor("j", SyncCursor(1L, "a"))
        val journal = Journal(listOf(doc(1L, "a"), doc(2L, "b"), doc(3L, "c"), doc(4L, "d")), pageSize = 2)
        var failOnce = true

        val first = state.syncJournal<String>(
            cursorId = "j",
            fetch = journal::page,
            apply = { if (failOnce) { failOnce = false; Result.failure(IllegalStateException("boom")) } else Result.success(Unit) },
        )
        assertTrue(first.isFailure)
        assertEquals(SyncCursor(1L, "a"), state.getCursor("j"), "курсор равен значению до этой страницы")

        state.syncJournal<String>(cursorId = "j", fetch = journal::page, apply = { Result.success(Unit) })

        assertEquals(listOf(SyncCursor(1L, "a"), SyncCursor(1L, "a"), SyncCursor(3L, "c")), journal.requests)
        assertEquals(SyncCursor(4L, "d"), state.getCursor("j"))
    }

    @Test
    fun `given an empty journal then exactly one request is made and the cursor is untouched`() = runTest {
        val state = InMemorySyncStateRepository()
        state.setCursor("j", SyncCursor(9L, "z"))
        val journal = Journal(emptyList(), pageSize = 2)

        val result = state.syncJournal<String>(cursorId = "j", fetch = journal::page, apply = { error("nothing to apply") })

        assertEquals(Result.success(0), result)
        assertEquals(1, journal.requests.size)
        assertEquals(SyncCursor(9L, "z"), state.getCursor("j"))
    }

    @Test
    fun `given a page of nothing but unreadable records then it is counted and the cursor passes it`() = runTest {
        // Иначе страница из битых записей останавливала бы журнал навсегда: курсор не двигался
        // бы, и следующий проход читал бы её же.
        val state = InMemorySyncStateRepository()
        val journal = Journal(listOf(doc(1L, "a", value = null), doc(2L, "b", value = null), doc(3L, "c")), pageSize = 2)
        val applied = mutableListOf<List<String>>()

        val result = state.syncJournal(cursorId = "j", fetch = journal::page, apply = { applied += it; Result.success(Unit) })

        assertEquals(Result.success(2), result)
        assertEquals(listOf(listOf("c")), applied)
        assertEquals(SyncCursor(3L, "c"), state.getCursor("j"))
    }

    @Test
    fun `given a record the caller cannot use then it counts as unreadable and is not applied`() = runTest {
        val state = InMemorySyncStateRepository()
        val journal = Journal(listOf(doc(1L, "a", value = ""), doc(2L, "b")), pageSize = 10)
        val applied = mutableListOf<List<String>>()

        val result = state.syncJournal(
            cursorId = "j",
            isReadable = { it.isNotBlank() },
            fetch = journal::page,
            apply = { applied += it; Result.success(Unit) },
        )

        assertEquals(Result.success(1), result)
        assertEquals(listOf(listOf("b")), applied)
    }

    @Test
    fun `given a page cap then the run reports incomplete and the next run resumes`() = runTest {
        val state = InMemorySyncStateRepository()
        val journal = Journal((1L..101L).map { doc(it, "d$it") }, pageSize = 2)

        val first = state.syncJournal<String>(
            cursorId = "j",
            fetch = journal::page,
            apply = { Result.success(Unit) },
        )

        assertTrue(first.isFailure)
        val limit = assertIs<SyncJournalPageLimitException>(first.exceptionOrNull())
        assertEquals("j", limit.cursorId)
        assertEquals(MAX_PAGES_PER_RUN, limit.pagesRead)
        assertEquals(SyncCursor(100L, "d100"), state.getCursor("j"))
        assertEquals(MAX_PAGES_PER_RUN, journal.requests.size)

        val resumed = state.syncJournal<String>(
            cursorId = "j",
            fetch = journal::page,
            apply = { Result.success(Unit) },
        )

        assertEquals(Result.success(0), resumed)
        assertEquals(SyncCursor(101L, "d101"), state.getCursor("j"))
    }

    @Test
    fun `given the final page ends at the page cap then the run succeeds`() = runTest {
        val state = InMemorySyncStateRepository()
        // 49 full pages and a single-record final page exercise the exact 50-page default cap.
        val journal = Journal((1L..99L).map { doc(it, "d$it") }, pageSize = 2)

        val result = state.syncJournal<String>(
            cursorId = "j",
            fetch = journal::page,
            apply = { Result.success(Unit) },
        )

        assertEquals(Result.success(0), result)
        assertEquals(50, journal.requests.size)
        assertEquals(SyncCursor(99L, "d99"), state.getCursor("j"))
    }

    @Test
    fun `given an explicit start then it wins over the saved cursor`() = runTest {
        val state = InMemorySyncStateRepository()
        state.setCursor("j", SyncCursor(9L, "z"))
        val journal = Journal(listOf(doc(1L, "a")), pageSize = 10)
        val applied = mutableListOf<List<String>>()

        state.syncJournal(cursorId = "j", startAt = SyncCursor.BEGINNING, fetch = journal::page, apply = { applied += it; Result.success(Unit) })

        assertEquals(listOf(listOf("a")), applied)
        assertEquals(SyncCursor(9L, "z"), state.getCursor("j"), "монотонность: назад курсор не откатывается")
    }

    @Test
    fun `given strict reading then an unreadable record fails the run and leaves the cursor`() = runTest {
        // Заголовочное требование 7.6: после backfill непонятой записи взяться неоткуда, и
        // встреченная означает, что клиент чего-то не знает. Прогон обязан отказать, а не пройти
        // мимо: иначе изменение и дальше выглядит для игрока как «изменений нет».
        val state = InMemorySyncStateRepository()
        val journal = Journal(listOf(doc(1L, "a"), doc(2L, "broken", value = null), doc(3L, "c")), pageSize = 3)
        val applied = mutableListOf<List<String>>()

        val result = state.syncJournal(
            cursorId = "j",
            policy = JournalReadPolicy.STRICT,
            fetch = journal::page,
            apply = { applied += it; Result.success(Unit) },
        )

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue(error is UnreadableJournalEntriesException, "тип отказа обязан отличаться от сетевого")
        assertEquals("j", error.cursorId)
        assertEquals(1, error.count)
        assertEquals(SyncCursor.BEGINNING, state.getCursor("j"), "курсор обязан остаться на месте")
        assertEquals(emptyList(), applied, "страница с непонятой записью не применяется по частям")
    }

    @Test
    fun `given strict reading then the next run reads the very same page again`() = runTest {
        val state = InMemorySyncStateRepository()
        val journal = Journal(listOf(doc(1L, "broken", value = null)), pageSize = 3)

        state.syncJournal<String>(cursorId = "j", policy = JournalReadPolicy.STRICT, fetch = journal::page, apply = { Result.success(Unit) })
        state.syncJournal<String>(cursorId = "j", policy = JournalReadPolicy.STRICT, fetch = journal::page, apply = { Result.success(Unit) })

        assertEquals(listOf(SyncCursor.BEGINNING, SyncCursor.BEGINNING), journal.requests)
    }

    @Test
    fun `given strict reading then a record the caller cannot use also stops the run`() = runTest {
        // `isReadable` — та же непонятность, только замеченная вызывающим: запись без id узла.
        val state = InMemorySyncStateRepository()
        val journal = Journal(listOf(doc(1L, "a"), doc(2L, "")), pageSize = 3)

        val result =
            state.syncJournal(
                cursorId = "j",
                policy = JournalReadPolicy.STRICT,
                isReadable = { it.isNotBlank() },
                fetch = journal::page,
                apply = { Result.success(Unit) },
            )

        assertTrue(result.isFailure)
        assertEquals(SyncCursor.BEGINNING, state.getCursor("j"))
    }

    @Test
    fun `given tolerant reading then the same record only gets counted`() = runTest {
        // До backfill ровно наоборот: первая же старая запись иначе остановила бы журнал навсегда.
        val state = InMemorySyncStateRepository()
        val journal = Journal(listOf(doc(1L, "a"), doc(2L, "broken", value = null)), pageSize = 3)

        val result = state.syncJournal(cursorId = "j", fetch = journal::page, apply = { Result.success(Unit) })

        assertEquals(Result.success(1), result)
        assertEquals(SyncCursor(2L, "broken"), state.getCursor("j"))
    }
}
