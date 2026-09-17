package com.tpov.schoolquiz.platform.firebase.quest_authoring

import com.tpov.schoolquiz.shared.core.sync.JournalReadPolicy
import com.tpov.schoolquiz.shared.core.sync.SyncCursor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Журнал ревью читается через callable, но правило то же, что у трёх журналов в Firestore (AD-31):
 * курсор — пара, страница — после неё, непонятое считается.
 */
class ReviewAssignmentChangePageTest {

    private fun change(id: String, ms: Long, docId: String? = null): Map<String, Any> =
        buildMap {
            put("id", id)
            put("changedAtMs", ms)
            if (docId != null) put("docId", docId)
        }

    @Test
    fun `the request carries the pair, the page size and a scalar one millisecond behind`() {
        val request = reviewAssignmentChangeRequest(SyncCursor(1_000L, "a-7"), limit = 200)

        assertEquals(mapOf("changedAtMs" to 1_000L, "docId" to "a-7"), request["cursor"])
        assertEquals(200, request["limit"])
        // Сервер прежней версии сравнивает строго с числом: чтобы он отдал всю миллисекунду
        // курсора, скаляр стоит на одну раньше.
        assertEquals(999L, request["cursorMs"])
        assertEquals(-1L, reviewAssignmentChangeRequest(SyncCursor.BEGINNING, 200)["cursorMs"])
    }

    @Test
    fun `given two records in the cursor millisecond then only the one after the pair is returned`() {
        val root = mapOf("changes" to listOf(change("a-1", 1_000L), change("a-2", 1_000L), change("a-3", 1_001L)))

        val page = reviewAssignmentChangePage(root, SyncCursor(1_000L, "a-1"), limit = 200)

        assertEquals(listOf("a-2", "a-3"), page.changes.map { it.id })
        assertEquals(SyncCursor(1_001L, "a-3"), page.nextCursor)
        assertFalse(page.hasMore)
    }

    @Test
    fun `given a bare-number cursor then the whole millisecond comes back and is ordered by the pair`() {
        val root = mapOf("changes" to listOf(change("a-2", 1_000L), change("a-1", 1_000L)))

        val page = reviewAssignmentChangePage(root, SyncCursor(1_000L, ""), limit = 200)

        assertEquals(listOf("a-1", "a-2"), page.changes.map { it.id })
        assertEquals(SyncCursor(1_000L, "a-2"), page.nextCursor)
    }

    @Test
    fun `given more records than the page then the page is cut and says there is more`() {
        val root = mapOf("changes" to (1L..3L).map { change("a-$it", it) })

        val page = reviewAssignmentChangePage(root, SyncCursor.BEGINNING, limit = 2)

        assertEquals(listOf("a-1", "a-2"), page.changes.map { it.id })
        assertEquals(SyncCursor(2L, "a-2"), page.nextCursor)
        assertTrue(page.hasMore)
    }

    @Test
    fun `given the server says whether there is more then its word wins`() {
        val root = mapOf("changes" to listOf(change("a-1", 1L)), "hasMore" to true)

        assertTrue(reviewAssignmentChangePage(root, SyncCursor.BEGINNING, limit = 200).hasMore)
    }

    @Test
    fun `given the server names the journal document then the cursor uses it`() {
        val root = mapOf("changes" to listOf(change("a-1", 1L, docId = "doc-1")))

        assertEquals(SyncCursor(1L, "doc-1"), reviewAssignmentChangePage(root, SyncCursor.BEGINNING, 200).nextCursor)
    }

    @Test
    fun `given a record without an assignment id then it is counted, not dropped, and the cursor passes it`() {
        val root = mapOf("changes" to listOf(mapOf("docId" to "legacy", "changedAtMs" to 5L), change("a-1", 6L)))

        val page = reviewAssignmentChangePage(root, SyncCursor.BEGINNING, limit = 200)

        assertEquals(listOf("a-1"), page.changes.map { it.id })
        assertEquals(1, page.unreadable)
        assertEquals(SyncCursor(6L, "a-1"), page.nextCursor)
    }

    @Test
    fun `given an empty or absent answer then the page is empty and the cursor does not move`() {
        assertEquals(null, reviewAssignmentChangePage(null, SyncCursor(5L, "x"), 200).nextCursor)
        assertEquals(
            null,
            reviewAssignmentChangePage(mapOf("changes" to emptyList<Any>()), SyncCursor(5L, "x"), 200).nextCursor,
        )
    }

    @Test
    fun `given a record without a timestamp then it is counted, not dropped before the count`() {
        // Такую запись отбрасывал `mapNotNull` ДО того, как страница считала непонятое: пропуск
        // снова не оставлял следа (AD-11).
        val root = mapOf("changes" to listOf(mapOf("id" to "a-0", "docId" to "doc-0"), change("a-1", 6L)))

        val page = reviewAssignmentChangePage(root, SyncCursor.BEGINNING, limit = 200)

        assertEquals(listOf("a-1"), page.changes.map { it.id })
        assertEquals(1, page.unreadable)
    }

    @Test
    fun `given a page of nothing but records without a timestamp then it does not look like an empty journal`() {
        // Пустой `changes`, `nextCursor = null` и `unreadable = 0` читались как «журнал кончился,
        // изменений нет»: рецензент не получал новых заданий, и ни одно число об этом не говорило.
        val root =
            mapOf(
                "changes" to
                    listOf(
                        mapOf("id" to "a-1", "docId" to "doc-1"),
                        mapOf("id" to "a-2", "docId" to "doc-2"),
                    ),
            )

        val page = reviewAssignmentChangePage(root, SyncCursor.BEGINNING, limit = 200)

        assertTrue(page.changes.isEmpty())
        assertEquals(2, page.unreadable)
    }

    @Test
    fun `given a negative timestamp then it is counted too`() {
        val root = mapOf("changes" to listOf(mapOf("id" to "a-1", "docId" to "doc-1", "changedAtMs" to -1L)))

        assertEquals(1, reviewAssignmentChangePage(root, SyncCursor.BEGINNING, limit = 200).unreadable)
    }

    @Test
    fun `given an element that is not an object at all then it is counted too`() {
        val root = mapOf("changes" to listOf("не объект", change("a-1", 6L)))

        val page = reviewAssignmentChangePage(root, SyncCursor.BEGINNING, limit = 200)

        assertEquals(listOf("a-1"), page.changes.map { it.id })
        assertEquals(1, page.unreadable)
    }

    @Test
    fun `given strict reading then the assignment id no longer stands in for the journal document id`() {
        // Прежняя форма: сервер не присылал `docId`. После backfill терпимость снимается тем же
        // переключателем, что и в остальных трёх журналах (AD-11).
        val root = mapOf("changes" to listOf(change("a-1", 6L)))

        val page = reviewAssignmentChangePage(root, SyncCursor.BEGINNING, 200, JournalReadPolicy.STRICT)

        assertTrue(page.changes.isEmpty())
        assertEquals(1, page.unreadable)
        assertEquals(
            listOf("a-1"),
            reviewAssignmentChangePage(root, SyncCursor.BEGINNING, 200).changes.map { it.id },
        )
    }
}
