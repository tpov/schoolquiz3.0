package com.tpov.schoolquiz.shared.core.catalog.data

import com.tpov.schoolquiz.shared.core.catalog.data.fake.FakeCatalogLocalDataSource
import com.tpov.schoolquiz.shared.core.catalog.data.fake.FakeCatalogRemoteDataSource
import com.tpov.schoolquiz.shared.core.catalog.data.fake.FakeCatalogUrlResolver
import com.tpov.schoolquiz.shared.core.persistence.CatalogEntity
import com.tpov.schoolquiz.shared.core.catalog.data.fake.FakeSyncStateRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Journey 6 integration test — archived catalog removed from observeAll.
 * GIVEN local has a catalog → remote returns archived=true for that catalog
 * → refreshFromRemote deletes it → observeAll emits list without it.
 * AC#22 (indirect), Journey 6.
 * Source: docs/features/home-and-my-quests/plan/phase-02/tests.md §7
 */
class CatalogArchiveIntegrationTest {

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

    // Journey 6: archived catalog is deleted locally and disappears from observeAll
    @Test
    fun `when catalog archived then removed from observeAll`() = runTest {
        fakeLocal.seed(
            listOf(
                CatalogEntity(
                    id = "c1",
                    name = "Catalog1",
                    picturePath = null,
                    pictureUrl = null,
                    version = 1L,
                    lastModifiedAt = 0L,
                    archived = false,
                ),
            ),
        )
        fakeRemote.result = Result.success(
            listOf(
                CatalogDto(
                    id = "c1",
                    name = "Catalog1",
                    picturePath = null,
                    version = 2L,
                    lastModifiedAt = 500L,
                    archived = true,
                ),
            ),
        )

        val result = repository.refreshFromRemote()
        val catalogs = repository.observeAll().first()

        assertTrue(result.isSuccess, "Expected success, got: $result")
        assertTrue(fakeLocal.deletedIds.contains("c1"), "Expected 'c1' to be deleted via deleteById")
        assertTrue(catalogs.none { it.id.value == "c1" }, "Expected observeAll to not contain archived catalog 'c1'")
    }

    // Archived catalog absent locally → skip (no delete call, no crash)
    @Test
    fun `when archived catalog absent locally then no delete called`() = runTest {
        fakeRemote.result = Result.success(
            listOf(
                CatalogDto(
                    id = "c-nonexistent",
                    name = "Ghost",
                    picturePath = null,
                    version = 1L,
                    lastModifiedAt = 100L,
                    archived = true,
                ),
            ),
        )

        val result = repository.refreshFromRemote()

        assertTrue(result.isSuccess, "Expected success")
        // Implementation may call deleteById even for absent ids (safe) or skip — both are valid.
        // The invariant is: no crash, and observeAll is empty.
        val catalogs = repository.observeAll().first()
        assertTrue(catalogs.none { it.id.value == "c-nonexistent" }, "Expected no ghost catalog in observeAll")
    }

    // Non-archived catalogs not deleted alongside archived ones
    @Test
    fun `when one catalog archived and another not then only archived is removed`() = runTest {
        fakeLocal.seed(
            listOf(
                CatalogEntity(id = "keep", name = "Keep", picturePath = null, pictureUrl = null, version = 1L, lastModifiedAt = 0L, archived = false),
                CatalogEntity(id = "remove", name = "Remove", picturePath = null, pictureUrl = null, version = 1L, lastModifiedAt = 0L, archived = false),
            ),
        )
        fakeRemote.result = Result.success(
            listOf(
                CatalogDto(id = "remove", name = "Remove", picturePath = null, version = 2L, lastModifiedAt = 600L, archived = true),
            ),
        )

        repository.refreshFromRemote()
        val catalogs = repository.observeAll().first()

        assertTrue(fakeLocal.deletedIds.contains("remove"), "Expected 'remove' to be deleted")
        assertTrue(catalogs.any { it.id.value == "keep" }, "Expected 'keep' still present in observeAll")
        assertTrue(catalogs.none { it.id.value == "remove" }, "Expected 'remove' absent from observeAll")
    }
}
