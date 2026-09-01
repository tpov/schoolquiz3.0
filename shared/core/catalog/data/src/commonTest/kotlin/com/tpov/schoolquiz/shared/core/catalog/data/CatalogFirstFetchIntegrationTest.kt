package com.tpov.schoolquiz.shared.core.catalog.data

import com.tpov.schoolquiz.shared.core.catalog.data.fake.FakeCatalogLocalDataSource
import com.tpov.schoolquiz.shared.core.catalog.data.fake.FakeCatalogRemoteDataSource
import com.tpov.schoolquiz.shared.core.catalog.data.fake.FakeCatalogUrlResolver
import com.tpov.schoolquiz.shared.core.catalog.data.fake.FakeSyncStateRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Journey integration test — "First-launch catalog pull" and delta-sync.
 * AC#19: first sync upserts all catalogs.
 * AC#20: delta sync uses cursor from previous sync, not 0.
 * Source: docs/features/home-and-my-quests/plan/phase-02/tests.md §6
 */
class CatalogFirstFetchIntegrationTest {

    private val fakeLocal = FakeCatalogLocalDataSource()
    private val fakeRemote = FakeCatalogRemoteDataSource()
    private val fakeUrlResolver = FakeCatalogUrlResolver()
    private val fakeSyncState = FakeSyncStateRepository()

    private val repository = CatalogRepositoryImpl(
        local = fakeLocal,
        remote = fakeRemote,
        storageUrlResolver = fakeUrlResolver.resolver,
        syncStateRepo = fakeSyncState,
    )

    // AC#19: first sync (cursor=0) → all returned catalogs are upserted locally
    @Test
    fun `given empty room and remote has catalog when refreshFromRemote then entity saved and url resolved`() = runTest {
        fakeRemote.result = Result.success(
            listOf(
                CatalogDto(
                    id = "a",
                    name = "A",
                    picturePath = "catalog-pictures/a.jpg",
                    version = 1L,
                    lastModifiedAt = 100L,
                    archived = false,
                ),
            ),
        )

        val result = repository.refreshFromRemote()

        assertTrue(result.isSuccess, "Expected success, got: $result")
        assertEquals(1, fakeLocal.upsertCallsFor["a"], "Expected upsert called once for catalog 'a'")
        val entity = fakeLocal.findById("a")
        assertNotNull(entity, "Expected entity to be stored")
        assertEquals("a", entity.id, "Expected entity id='a'")
        assertEquals(1, fakeUrlResolver.callCount, "Expected url resolver called once for catalog-pictures/a.jpg")
    }

    // AC#19: first sync with empty remote → nothing upserted, cursor not advanced
    @Test
    fun `given empty room and remote returns empty list when refreshFromRemote then nothing upserted`() = runTest {
        fakeRemote.result = Result.success(emptyList())

        val result = repository.refreshFromRemote()

        assertTrue(result.isSuccess, "Expected success even for empty remote")
        assertTrue(fakeLocal.upsertCallsFor.isEmpty(), "Expected no upserts when remote empty")
        assertTrue(fakeLocal.deletedIds.isEmpty(), "Expected no deletes when remote empty")
        // cursor must NOT advance when dtos are empty (backend.md task #3 edge case)
        assertTrue(fakeSyncState.setCursorCalls.isEmpty(), "Expected cursor not advanced for empty result")
    }

    // AC#19: first sync upserts multiple catalogs (spec: 4 catalogs)
    @Test
    fun `given remote returns 4 catalogs when first sync then all 4 upserted`() = runTest {
        fakeRemote.result = Result.success(
            listOf(
                CatalogDto(id = "c1", name = "C1", picturePath = null, version = 1L, lastModifiedAt = 100L, archived = false),
                CatalogDto(id = "c2", name = "C2", picturePath = null, version = 1L, lastModifiedAt = 200L, archived = false),
                CatalogDto(id = "c3", name = "C3", picturePath = null, version = 1L, lastModifiedAt = 300L, archived = false),
                CatalogDto(id = "c4", name = "C4", picturePath = null, version = 1L, lastModifiedAt = 400L, archived = false),
            ),
        )

        repository.refreshFromRemote()

        assertEquals(1, fakeLocal.upsertCallsFor["c1"] ?: 0, "Expected c1 upserted once")
        assertEquals(1, fakeLocal.upsertCallsFor["c2"] ?: 0, "Expected c2 upserted once")
        assertEquals(1, fakeLocal.upsertCallsFor["c3"] ?: 0, "Expected c3 upserted once")
        assertEquals(1, fakeLocal.upsertCallsFor["c4"] ?: 0, "Expected c4 upserted once")
    }

    // AC#19: observeAll returns upserted catalogs after first sync
    @Test
    fun `given first sync succeeds when observeAll then emits upserted catalogs`() = runTest {
        fakeRemote.result = Result.success(
            listOf(
                CatalogDto(id = "x", name = "X", picturePath = null, version = 1L, lastModifiedAt = 50L, archived = false),
            ),
        )
        repository.refreshFromRemote()

        val catalogs = repository.observeAll().first()

        assertEquals(1, catalogs.size)
        assertEquals("x", catalogs[0].id.value)
    }

    // AC#20: delta sync uses cursor from previous sync (not 0)
    @Test
    fun `delta sync after first fetch reads only changed`() = runTest {
        // Simulate previous sync having advanced cursor to 1000L
        fakeLocal.seed(
            listOf(
                com.tpov.schoolquiz.shared.core.persistence.CatalogEntity(
                    id = "a",
                    name = "A",
                    picturePath = null,
                    pictureUrl = null,
                    version = 1L,
                    lastModifiedAt = 100L,
                ),
            ),
        )
        fakeSyncState.setCursor("catalogs", 1000L)
        fakeRemote.result = Result.success(
            listOf(
                CatalogDto(id = "b", name = "B", picturePath = null, version = 2L, lastModifiedAt = 2000L, archived = false),
            ),
        )

        repository.refreshFromRemote()

        assertEquals(1000L, fakeRemote.lastCursor, "Expected second sync to pass cursor=1000L, not 0")
    }
}
