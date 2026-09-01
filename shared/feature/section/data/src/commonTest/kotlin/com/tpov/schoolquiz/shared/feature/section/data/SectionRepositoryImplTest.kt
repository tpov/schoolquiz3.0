package com.tpov.schoolquiz.shared.feature.section.data

import com.tpov.schoolquiz.shared.core.persistence.SectionEntity
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import com.tpov.schoolquiz.shared.feature.section.data.dto.SectionDto
import com.tpov.schoolquiz.shared.feature.section.data.fake.FakeSectionLocalDataSource
import com.tpov.schoolquiz.shared.feature.section.data.fake.FakeSectionRemoteDataSource
import com.tpov.schoolquiz.shared.feature.section.domain.model.SectionId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * JVM tests for SectionRepositoryImpl.
 * Source: docs/features/home-and-my-quests/plan/phase-02/tests.md §3
 */
class SectionRepositoryImplTest {

    private val fakeLocal = FakeSectionLocalDataSource()
    private val fakeRemote = FakeSectionRemoteDataSource()
    private val repository = SectionRepositoryImpl(local = fakeLocal, remote = fakeRemote)

    private val questIds = setOf(QuestId("q1"))

    private fun makeDto(
        id: String = "s1",
        questId: String = "q1",
        title: String = "Section",
        order: Int = 0,
        version: Long = 1L,
        lastModifiedAt: Long = 100L,
        archived: Boolean = false,
    ) = SectionDto(id, questId, title, order, version, lastModifiedAt, archived)

    private fun makeEntity(
        id: String,
        version: Long = 1L,
        title: String = "Section",
    ) = SectionEntity(id, "q1", title, 0, version, 0L, 0L, false)

    // Matrix 2.2: section absent locally + not archived → inserted
    @Test
    fun `when section absent and not archived then inserted`() = runTest {
        fakeRemote.result = listOf(makeDto("s1"))

        val result = repository.refreshByParents(questIds, 0L)

        assertTrue(result.isSuccess)
        assertEquals(1, fakeLocal.upsertCallsFor["s1"] ?: 0)
        assertEquals(setOf(SectionId("s1")), result.getOrThrow())
    }

    // Matrix 2.3: section archived + newer version → deleted locally
    @Test
    fun `when section archived and newer version then deleted`() = runTest {
        fakeLocal.seed(listOf(makeEntity("s1", version = 1L)))
        fakeRemote.result = listOf(makeDto("s1", version = 2L, archived = true))

        val result = repository.refreshByParents(questIds, 0L)

        assertTrue(result.isSuccess)
        assertTrue(fakeLocal.deletedIds.contains("s1"))
        assertNull(fakeLocal.findById("s1"))
    }

    // Matrix 2.5: dto.version == local.version → upsert called but entity not overwritten
    @Test
    fun `when section version equal then upsert called but entity not overwritten`() = runTest {
        fakeLocal.seed(listOf(makeEntity("s1", version = 5L, title = "Original")))
        fakeRemote.result = listOf(makeDto("s1", version = 5L, title = "Updated"))

        repository.refreshByParents(questIds, 0L)

        assertEquals(1, fakeLocal.upsertCallsFor["s1"] ?: 0)
        assertEquals("Original", fakeLocal.findById("s1")?.title)
    }

    @Test
    fun `when remote section version is not newer then id is still returned`() = runTest {
        fakeLocal.seed(listOf(makeEntity("s1", version = 5L)))
        fakeRemote.result = listOf(makeDto("s1", version = 5L))

        val result = repository.refreshByParents(questIds, 0L)

        assertEquals(setOf(SectionId("s1")), result.getOrThrow())
    }

    // refreshByParents succeeds → Result.success
    @Test
    fun `when refreshByParents succeeds then result is success`() = runTest {
        fakeRemote.result = listOf(makeDto("s1"))

        val result = repository.refreshByParents(questIds, 0L)

        assertTrue(result.isSuccess)
    }

    // Remote throws → Result.failure
    @Test
    fun `when refreshByParents fails then failure result`() = runTest {
        fakeRemote.shouldThrow = true

        val result = repository.refreshByParents(questIds, 0L)

        assertTrue(result.isFailure)
    }

    // Empty questIds → no-op, no remote call, success returned
    @Test
    fun `when questIds empty then no remote call and success returned`() = runTest {
        val result = repository.refreshByParents(emptySet(), 0L)

        assertTrue(result.isSuccess)
        assertEquals(0, fakeRemote.callCount)
    }

    // Cursor passed to remote
    @Test
    fun `when cursor 500 then remote receives cursor 500`() = runTest {
        fakeRemote.result = emptyList()

        repository.refreshByParents(questIds, 500L)

        assertEquals(500L, fakeRemote.lastCursor)
    }

    // Non-archived section upserted normally
    @Test
    fun `when section not archived then upserted`() = runTest {
        fakeRemote.result = listOf(makeDto("s2", archived = false))

        repository.refreshByParents(setOf(QuestId("q1")), 0L)

        assertEquals(1, fakeLocal.upsertCallsFor["s2"] ?: 0)
    }
}
