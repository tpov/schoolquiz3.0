package com.tpov.schoolquiz.shared.core.catalog.data

import com.tpov.schoolquiz.shared.core.catalog.data.fake.FakeCatalogLocalDataSource
import com.tpov.schoolquiz.shared.core.catalog.data.fake.FakeCatalogRemoteDataSource
import com.tpov.schoolquiz.shared.core.catalog.data.fake.FakeCatalogUrlResolver
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.core.persistence.CatalogEntity
import com.tpov.schoolquiz.shared.core.catalog.data.fake.FakeSyncStateRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Journey integration test — "Warm cache (Room → UI)".
 * Pre-populated local → observeAll without Firestore call, sorted ASC.
 * Source: docs/features/menu-refactor/04-testing.md §5.3
 */
class CatalogWarmCacheIntegrationTest {

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

    @Test
    fun `given pre-populated local when observeAll then returns sorted ASC without Firestore call`() = runTest {
        fakeLocal.emit(
            listOf(
                CatalogEntity(id = "b", name = "B", picturePath = null, pictureUrl = null),
                CatalogEntity(id = "a", name = "A", picturePath = null, pictureUrl = null),
            ),
        )

        val catalogs = repository.observeAll().first()

        assertEquals(CatalogId("a"), catalogs[0].id, "Expected first catalog sorted as 'a'")
        assertEquals(CatalogId("b"), catalogs[1].id, "Expected second catalog sorted as 'b'")
        assertEquals(0, fakeRemote.fetchChangedSinceCalls, "Firestore must NOT be called when reading from warm cache")
    }
}
