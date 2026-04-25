package com.tpov.schoolquiz.shared.core.catalog.data

import com.tpov.schoolquiz.shared.core.catalog.data.fake.FakeCatalogLocalDataSource
import com.tpov.schoolquiz.shared.core.catalog.data.fake.FakeCatalogRemoteDataSource
import com.tpov.schoolquiz.shared.core.catalog.data.fake.FakeCatalogUrlResolver
import com.tpov.schoolquiz.shared.core.catalog.data.fake.FakeSyncStateRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Journey integration test — "Offline launch".
 * Empty Room + Firestore error → emptyList, no crash.
 * Source: docs/features/menu-refactor/04-testing.md §5.3
 */
class CatalogOfflineEmptyIntegrationTest {

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
    fun `given empty room and offline when observeAll then emits empty list without exception`() = runTest {
        fakeRemote.result = Result.failure(Exception("offline"))

        val catalogs = repository.observeAll().first()

        assertTrue(catalogs.isEmpty(), "Expected empty list from empty Room, got: $catalogs")
    }

    @Test
    fun `given empty room and offline when refreshFromRemote then returns failure and nothing is written`() = runTest {
        fakeRemote.result = Result.failure(Exception("offline"))

        val result = repository.refreshFromRemote()

        assertTrue(result.isFailure, "Expected Result.failure when offline, got success")
        assertTrue(fakeLocal.upsertCallsFor.isEmpty(), "Expected no upserts when remote throws")
        assertTrue(fakeLocal.deletedIds.isEmpty(), "Expected no deletes when remote throws")
        assertTrue(fakeSyncState.setCursorCalls.isEmpty(), "Expected cursor not advanced on failure")
    }
}
