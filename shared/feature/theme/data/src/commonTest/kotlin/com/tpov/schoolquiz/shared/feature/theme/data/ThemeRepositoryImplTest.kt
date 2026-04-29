package com.tpov.schoolquiz.shared.feature.theme.data

import com.tpov.schoolquiz.shared.core.persistence.ThemeEntity
import com.tpov.schoolquiz.shared.feature.section.domain.model.SectionId
import com.tpov.schoolquiz.shared.feature.theme.data.dto.ThemeDto
import com.tpov.schoolquiz.shared.feature.theme.data.fake.FakeThemeLocalDataSource
import com.tpov.schoolquiz.shared.feature.theme.data.fake.FakeThemeRemoteDataSource
import com.tpov.schoolquiz.shared.feature.theme.domain.model.ThemeId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * JVM tests for ThemeRepositoryImpl.
 * Source: docs/features/home-and-my-quests/plan/phase-02/tests.md §3 (Theme pattern)
 * NOTE: Depends on backend-dev phase-02 task #8 (theme/data module creation).
 * This file compiles once ThemeRepositoryImpl, ThemeLocalDataSource,
 * ThemeRemoteDataSource, and ThemeDto exist in shared/feature/theme/data.
 */
class ThemeRepositoryImplTest {

    private val fakeLocal = FakeThemeLocalDataSource()
    private val fakeRemote = FakeThemeRemoteDataSource()
    private val repository = ThemeRepositoryImpl(local = fakeLocal, remote = fakeRemote)

    private val sectionIds = setOf(SectionId("sec1"))

    private fun makeDto(
        id: String = "t1",
        sectionId: String = "sec1",
        title: String = "Theme",
        order: Int = 0,
        version: Long = 1L,
        contentsVersion: Long = 0L,
        lastModifiedAt: Long = 100L,
        archived: Boolean = false,
    ) = ThemeDto(id, sectionId, title, order, version, contentsVersion, lastModifiedAt, archived)

    private fun makeEntity(
        id: String,
        version: Long = 1L,
        title: String = "Theme",
        contentsVersion: Long = 0L,
    ) = ThemeEntity(id, "sec1", title, 0, version, contentsVersion, 0L, false)

    // Matrix 2 row: theme absent + not archived → inserted
    @Test
    fun `when theme absent and not archived then inserted`() = runTest {
        fakeRemote.result = listOf(makeDto("t1"))

        val result = repository.refreshByParents(sectionIds, 0L)

        assertTrue(result.isSuccess)
        assertEquals(1, fakeLocal.upsertCallsFor["t1"] ?: 0)
    }

    // Matrix 2 row EDGE 2.7: theme archived + newer version → deleted
    @Test
    fun `when theme archived and newer version then deleted`() = runTest {
        fakeLocal.seed(listOf(makeEntity("t1", version = 1L)))
        fakeRemote.result = listOf(makeDto("t1", version = 2L, archived = true))

        val result = repository.refreshByParents(sectionIds, 0L)

        assertTrue(result.isSuccess)
        assertTrue(fakeLocal.deletedIds.contains("t1"))
        assertNull(fakeLocal.findById("t1"))
    }

    // Matrix 2 row: version equal → upsert called but entity not overwritten
    @Test
    fun `when theme version equal then upsert called but entity not overwritten`() = runTest {
        fakeLocal.seed(listOf(makeEntity("t1", version = 5L, title = "Original")))
        fakeRemote.result = listOf(makeDto("t1", version = 5L, title = "Updated"))

        repository.refreshByParents(sectionIds, 0L)

        assertEquals(1, fakeLocal.upsertCallsFor["t1"] ?: 0)
        assertEquals("Original", fakeLocal.findById("t1")?.title)
    }

    @Test
    fun `when remote theme returned with same contents version then id still cascades`() = runTest {
        fakeLocal.seed(listOf(makeEntity("t1", version = 5L, contentsVersion = 10L)))
        fakeRemote.result = listOf(makeDto("t1", version = 5L, contentsVersion = 10L))

        val result = repository.refreshByParents(sectionIds, 0L)

        assertEquals(setOf(ThemeId("t1")), result.getOrThrow())
    }

    // refreshByParents succeeds → Result.success
    @Test
    fun `when refreshByParents succeeds then result is success`() = runTest {
        fakeRemote.result = listOf(makeDto("t1"))

        val result = repository.refreshByParents(sectionIds, 0L)

        assertTrue(result.isSuccess)
    }

    // Remote throws → Result.failure
    @Test
    fun `when refreshByParents fails then failure result`() = runTest {
        fakeRemote.shouldThrow = true

        val result = repository.refreshByParents(sectionIds, 0L)

        assertTrue(result.isFailure)
    }

    // Empty sectionIds → no-op, no remote call
    @Test
    fun `when sectionIds empty then no remote call and success returned`() = runTest {
        val result = repository.refreshByParents(emptySet(), 0L)

        assertTrue(result.isSuccess)
        assertEquals(0, fakeRemote.callCount)
    }
}
