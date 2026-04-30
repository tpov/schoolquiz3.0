package com.tpov.schoolquiz.shared.core.sync.fake

import com.tpov.schoolquiz.shared.core.catalog.domain.model.Catalog
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.core.catalog.domain.repository.CatalogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Sync-module fake for [CatalogRepository].
 *
 * After [refreshFromRemote] succeeds, [observeAll] emits catalogs whose IDs
 * are in [nextChangedIds]. The orchestrator reads this post-refresh list to
 * determine which catalog IDs to pass to the quest sync step.
 *
 * Setting [nextChangedIds] = emptySet() simulates "catalog cv unchanged" —
 * observeAll returns an empty list, so the orchestrator skips quest sync.
 */
class FakeCatalogRepository : CatalogRepository {

    private val cache = MutableStateFlow<Map<CatalogId, Catalog>>(emptyMap())

    var refreshCalls = 0
    var refreshByIdsCallCount = 0
    var lastRefreshByIds: Set<CatalogId> = emptySet()
    var nextChangedIds: Set<CatalogId> = emptySet()
    var nextLocalCvMap: Map<CatalogId, Long> = emptyMap()
    private var nextRefreshFailure: Throwable? = null

    override fun observeAll(): Flow<List<Catalog>> =
        cache.map { it.values.sortedBy { c -> c.id.value } }

    override suspend fun refreshFromRemote(): Result<Set<CatalogId>> {
        refreshCalls++
        val failure = nextRefreshFailure
        if (failure != null) {
            nextRefreshFailure = null
            return Result.failure(failure)
        }
        cache.value = nextChangedIds.associateWith { id ->
            Catalog(
                id = id,
                name = "catalog-${id.value}",
                picturePath = null,
                pictureUrl = null,
                version = 1L,
                contentsVersion = 1L,
                lastModifiedAt = 1_000L,
                archived = false,
            )
        }
        return Result.success(nextChangedIds)
    }

    override suspend fun refreshByIds(ids: Set<CatalogId>): Result<Unit> {
        refreshByIdsCallCount++
        lastRefreshByIds = ids
        return Result.success(Unit)
    }

    override suspend fun getById(id: CatalogId): Catalog? = cache.value[id]

    fun seedWithLocalCv(catalogId: String, localCv: Long) {
        nextLocalCvMap = nextLocalCvMap + (CatalogId(catalogId) to localCv)
    }

    fun setNextRefreshFailure(error: Throwable) {
        nextRefreshFailure = error
    }

    fun seed(catalogs: List<Catalog>) {
        cache.value = catalogs.associateBy { it.id }
    }

    fun resetAll() {
        refreshCalls = 0
        refreshByIdsCallCount = 0
        lastRefreshByIds = emptySet()
        nextChangedIds = emptySet()
        nextLocalCvMap = emptyMap()
        nextRefreshFailure = null
        cache.value = emptyMap()
    }
}
