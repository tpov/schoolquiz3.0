package com.tpov.schoolquiz.shared.core.sync

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class InMemorySyncStateRepositoryTest {

    private val repository = InMemorySyncStateRepository()

    @Test
    fun `getCursor returns the beginning by default`() = runTest {
        assertEquals(SyncCursor.BEGINNING, repository.getCursor("quizzes"))
    }

    @Test
    fun `setCursor and getCursor keep the whole pair`() = runTest {
        repository.setCursor("quizzes", SyncCursor(1714500000000L, "doc-b"))

        assertEquals(SyncCursor(1714500000000L, "doc-b"), repository.getCursor("quizzes"))
    }

    @Test
    fun `setCursor for different collections are independent`() = runTest {
        repository.setCursor("quizzes", SyncCursor(1000L, "a"))
        repository.setCursor("questions", SyncCursor(2000L, "b"))

        assertEquals(SyncCursor(1000L, "a"), repository.getCursor("quizzes"))
        assertEquals(SyncCursor(2000L, "b"), repository.getCursor("questions"))
        assertEquals(SyncCursor.BEGINNING, repository.getCursor("categories"))
    }

    @Test
    fun `setCursor moves forward by the pair, never back`() = runTest {
        repository.setCursor("quizzes", SyncCursor(1000L, "b"))

        // Та же миллисекунда, меньший id — это раньше, а не позже.
        repository.setCursor("quizzes", SyncCursor(1000L, "a"))
        assertEquals(SyncCursor(1000L, "b"), repository.getCursor("quizzes"))

        repository.setCursor("quizzes", SyncCursor(999L, "z"))
        assertEquals(SyncCursor(1000L, "b"), repository.getCursor("quizzes"))

        repository.setCursor("quizzes", SyncCursor(1000L, "c"))
        assertEquals(SyncCursor(1000L, "c"), repository.getCursor("quizzes"))
    }

    @Test
    fun `resetAllCursors puts every collection back to the beginning`() = runTest {
        repository.setCursor("quizzes", SyncCursor(1000L, "a"))

        repository.resetAllCursors()

        assertEquals(SyncCursor.BEGINNING, repository.getCursor("quizzes"))
    }
}
