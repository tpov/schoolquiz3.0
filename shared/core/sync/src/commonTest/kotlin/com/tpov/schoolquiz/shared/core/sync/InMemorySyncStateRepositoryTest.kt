package com.tpov.schoolquiz.shared.core.sync

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class InMemorySyncStateRepositoryTest {

    private val repository = InMemorySyncStateRepository()

    @Test
    fun `getCursor returns 0 by default`() = runTest {
        val cursor = repository.getCursor("quizzes")
        assertEquals(0L, cursor)
    }

    @Test
    fun `setCursor and getCursor returns value`() = runTest {
        repository.setCursor("quizzes", 1714500000000L)
        val cursor = repository.getCursor("quizzes")
        assertEquals(1714500000000L, cursor)
    }

    @Test
    fun `setCursor for different collections are independent`() = runTest {
        repository.setCursor("quizzes", 1000L)
        repository.setCursor("questions", 2000L)

        assertEquals(1000L, repository.getCursor("quizzes"))
        assertEquals(2000L, repository.getCursor("questions"))
        assertEquals(0L, repository.getCursor("categories"))
    }

    @Test
    fun `setCursor overwrites previous value`() = runTest {
        repository.setCursor("quizzes", 1000L)
        repository.setCursor("quizzes", 5000L)
        assertEquals(5000L, repository.getCursor("quizzes"))
    }
}
