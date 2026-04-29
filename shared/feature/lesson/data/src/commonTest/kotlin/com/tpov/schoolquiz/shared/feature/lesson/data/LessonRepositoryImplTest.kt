package com.tpov.schoolquiz.shared.feature.lesson.data

import com.tpov.schoolquiz.shared.core.persistence.LessonEntity
import com.tpov.schoolquiz.shared.feature.lesson.data.dto.LessonDto
import com.tpov.schoolquiz.shared.feature.lesson.data.fake.FakeLessonLocalDataSource
import com.tpov.schoolquiz.shared.feature.lesson.data.fake.FakeLessonRemoteDataSource
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.theme.domain.model.ThemeId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * JVM tests for LessonRepositoryImpl.
 * Source: docs/features/home-and-my-quests/plan/phase-02/tests.md §3 (Lesson pattern)
 *
 * OPEN QUESTION: tests.md specifies refreshByParents should return Result<Set<LessonId>>
 * for cascade trigger, but LessonRepository.refreshByParents returns Result<Unit>.
 * Tests verify side-effects instead. Pending domain interface clarification from lead.
 */
class LessonRepositoryImplTest {

    private val fakeLocal = FakeLessonLocalDataSource()
    private val fakeRemote = FakeLessonRemoteDataSource()
    private val repository = LessonRepositoryImpl(local = fakeLocal, remote = fakeRemote)

    private val themeIds = setOf(ThemeId("th1"))

    private fun makeDto(
        id: String = "l1",
        themeId: String = "th1",
        title: String = "Lesson",
        order: Int = 0,
        version: Long = 1L,
        contentsVersion: Long = 0L,
        lastModifiedAt: Long = 100L,
        archived: Boolean = false,
    ) = LessonDto(id, themeId, title, order, version, contentsVersion, lastModifiedAt, archived)

    private fun makeEntity(
        id: String,
        version: Long = 1L,
        title: String = "Lesson",
        contentsVersion: Long = 0L,
    ) = LessonEntity(id, "th1", title, 0, version, contentsVersion, 0L, false)

    // Matrix 2 row: lesson absent + not archived → inserted
    @Test
    fun `when lesson absent and not archived then inserted`() = runTest {
        fakeRemote.result = listOf(makeDto("l1"))

        val result = repository.refreshByParents(themeIds, 0L)

        assertTrue(result.isSuccess)
        assertEquals(1, fakeLocal.upsertCallsFor["l1"] ?: 0)
    }

    // Matrix 2 row EDGE: lesson archived + newer version → deleted
    @Test
    fun `when lesson archived and newer version then deleted`() = runTest {
        fakeLocal.seed(listOf(makeEntity("l1", version = 1L)))
        fakeRemote.result = listOf(makeDto("l1", version = 2L, archived = true))

        val result = repository.refreshByParents(themeIds, 0L)

        assertTrue(result.isSuccess)
        assertTrue(fakeLocal.deletedIds.contains("l1"))
        assertNull(fakeLocal.findById("l1"))
    }

    // Matrix 2 row: version equal → upsert called but entity not overwritten
    @Test
    fun `when lesson version equal then upsert called but entity not overwritten`() = runTest {
        fakeLocal.seed(listOf(makeEntity("l1", version = 5L, title = "Original")))
        fakeRemote.result = listOf(makeDto("l1", version = 5L, title = "Updated"))

        repository.refreshByParents(themeIds, 0L)

        assertEquals(1, fakeLocal.upsertCallsFor["l1"] ?: 0)
        assertEquals("Original", fakeLocal.findById("l1")?.title)
    }

    @Test
    fun `when remote lesson returned with same contents version then id still cascades`() = runTest {
        fakeLocal.seed(listOf(makeEntity("l1", version = 5L, contentsVersion = 10L)))
        fakeRemote.result = listOf(makeDto("l1", version = 5L, contentsVersion = 10L))

        val result = repository.refreshByParents(themeIds, 0L)

        assertEquals(setOf(LessonId("l1")), result.getOrThrow())
    }

    // refreshByParents succeeds → Result.success
    @Test
    fun `when refreshByParents succeeds then result is success`() = runTest {
        fakeRemote.result = listOf(makeDto("l1"))

        val result = repository.refreshByParents(themeIds, 0L)

        assertTrue(result.isSuccess)
    }

    // Remote throws → Result.failure
    @Test
    fun `when refreshByParents fails then failure result`() = runTest {
        fakeRemote.shouldThrow = true

        val result = repository.refreshByParents(themeIds, 0L)

        assertTrue(result.isFailure)
    }

    // Empty themeIds → no-op, no remote call, success returned
    @Test
    fun `when themeIds empty then no remote call and success returned`() = runTest {
        val result = repository.refreshByParents(emptySet(), 0L)

        assertTrue(result.isSuccess)
        assertEquals(0, fakeRemote.callCount)
    }

    // Cursor passed to remote
    @Test
    fun `when cursor 500 then remote receives cursor 500`() = runTest {
        fakeRemote.result = emptyList()

        repository.refreshByParents(themeIds, 500L)

        assertEquals(500L, fakeRemote.lastCursor)
    }

    // Non-archived lesson upserted normally
    @Test
    fun `when lesson not archived then upserted`() = runTest {
        fakeRemote.result = listOf(makeDto("l2", archived = false))

        repository.refreshByParents(setOf(ThemeId("th1")), 0L)

        assertEquals(1, fakeLocal.upsertCallsFor["l2"] ?: 0)
    }

    // themeIds mapped correctly to remote call
    @Test
    fun `when themeIds provided then remote receives theme id strings`() = runTest {
        fakeRemote.result = emptyList()

        repository.refreshByParents(setOf(ThemeId("th1"), ThemeId("th2")), 0L)

        assertTrue(fakeRemote.lastThemeIds.contains("th1"))
        assertTrue(fakeRemote.lastThemeIds.contains("th2"))
        assertEquals(2, fakeRemote.lastThemeIds.size)
    }
}
