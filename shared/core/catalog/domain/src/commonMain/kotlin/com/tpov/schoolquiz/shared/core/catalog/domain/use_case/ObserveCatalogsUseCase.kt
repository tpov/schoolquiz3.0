package com.tpov.schoolquiz.shared.core.catalog.domain.use_case

import com.tpov.schoolquiz.shared.core.catalog.domain.model.Catalog
import com.tpov.schoolquiz.shared.core.catalog.domain.repository.CatalogRepository
import kotlinx.coroutines.flow.Flow

/**
 * Thin wrapper around [CatalogRepository.observeAll].
 *
 * ViewModels depend on this use case rather than the repository directly,
 * so the repository can be swapped without touching UI code. The use case
 * also serves as a stable integration point for future orchestration
 * (loading state, refresh-on-stale, …) if it ever grows beyond pass-through.
 *
 * Spec: docs/features/catalog-foundation/0-spec.md — Primary User Journey 1
 * (first-launch observe) and Journey 2 (warm-cache observe).
 */
class ObserveCatalogsUseCase(
    private val catalogs: CatalogRepository,
) {
    operator fun invoke(): Flow<List<Catalog>> = catalogs.observeAll()
}
