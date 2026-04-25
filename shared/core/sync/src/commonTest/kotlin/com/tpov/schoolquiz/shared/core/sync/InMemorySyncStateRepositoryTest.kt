package com.tpov.schoolquiz.shared.core.sync

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
    fun `markCascadeInProgress tracked in getPendingCascades`() = runTest {
        repository.markCascadeInProgress(
            parentId = "quiz-123",
            parentType = "Quiz",
            pendingChildIds = setOf("q-1", "q-2", "q-3"),
        )
        val pending = repository.getPendingCascades()
        assertEquals(1, pending.size)
        val cascade = pending.first()
        assertEquals("quiz-123", cascade.parentId)
        assertEquals("Quiz", cascade.parentType)
        assertEquals(setOf("q-1", "q-2", "q-3"), cascade.pendingChildIds)
    }

    @Test
    fun `markCascadeCompleted removes from pending`() = runTest {
        repository.markCascadeInProgress(
            parentId = "quiz-123",
            parentType = "Quiz",
            pendingChildIds = setOf("q-1", "q-2"),
        )
        repository.markCascadeCompleted(parentId = "quiz-123", parentType = "Quiz")
        val pending = repository.getPendingCascades()
        assertTrue(pending.isEmpty())
    }

    @Test
    fun `multiple cascades tracked independently`() = runTest {
        repository.markCascadeInProgress(
            parentId = "quiz-A",
            parentType = "Quiz",
            pendingChildIds = setOf("q-1"),
        )
        repository.markCascadeInProgress(
            parentId = "quiz-B",
            parentType = "Quiz",
            pendingChildIds = setOf("q-2", "q-3"),
        )
        repository.markCascadeCompleted(parentId = "quiz-A", parentType = "Quiz")

        val pending = repository.getPendingCascades()
        assertEquals(1, pending.size)
        assertEquals("quiz-B", pending.first().parentId)
        assertEquals(setOf("q-2", "q-3"), pending.first().pendingChildIds)
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

    @Test
    fun `markCascadeCompleted on non-existent cascade is a no-op`() = runTest {
        repository.markCascadeCompleted(parentId = "ghost-id", parentType = "Quiz")
        val pending = repository.getPendingCascades()
        assertTrue(pending.isEmpty())
    }

    @Test
    fun `markCascadeInProgress overwrites previous pending children for same parent`() = runTest {
        repository.markCascadeInProgress(
            parentId = "quiz-X",
            parentType = "Quiz",
            pendingChildIds = setOf("old-child"),
        )
        repository.markCascadeInProgress(
            parentId = "quiz-X",
            parentType = "Quiz",
            pendingChildIds = setOf("new-child-1", "new-child-2"),
        )
        val pending = repository.getPendingCascades()
        assertEquals(1, pending.size)
        assertEquals(setOf("new-child-1", "new-child-2"), pending.first().pendingChildIds)
    }

    @Test
    fun `getPendingCascades returns all in-progress cascades`() = runTest {
        repository.markCascadeInProgress("p-1", "Quiz", setOf("c-1"))
        repository.markCascadeInProgress("p-2", "Quiz", setOf("c-2"))
        repository.markCascadeInProgress("p-3", "Category", setOf("c-3"))

        val pending = repository.getPendingCascades()
        assertEquals(3, pending.size)
        val parentIds = pending.map { it.parentId }.toSet()
        assertTrue(parentIds.contains("p-1"))
        assertTrue(parentIds.contains("p-2"))
        assertTrue(parentIds.contains("p-3"))
    }

    @Test
    fun `cascade with same parentId but different parentType are independent`() = runTest {
        repository.markCascadeInProgress("entity-1", "Quiz", setOf("c-quiz"))
        repository.markCascadeInProgress("entity-1", "Category", setOf("c-cat"))
        repository.markCascadeCompleted("entity-1", "Quiz")

        val pending = repository.getPendingCascades()
        assertEquals(1, pending.size)
        assertEquals("Category", pending.first().parentType)
        assertEquals(setOf("c-cat"), pending.first().pendingChildIds)
    }
}
