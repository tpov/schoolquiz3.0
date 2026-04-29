package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake

import com.tpov.schoolquiz.shared.core.catalog.domain.model.Catalog
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.core.catalog.domain.repository.CatalogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Minimal stub for tests that only need the QuizzesComponent's catalog
 * resolution path to be a no-op. Returns null/empty for everything.
 */
class StubCatalogRepository(
    private val byId: Map<CatalogId, Catalog> = emptyMap(),
) : CatalogRepository {
    override fun observeAll(): Flow<List<Catalog>> = flowOf(byId.values.toList())

    override suspend fun refreshFromRemote(): Result<Set<CatalogId>> =
        Result.success(byId.keys)

    override suspend fun getById(id: CatalogId): Catalog? = byId[id]
}
