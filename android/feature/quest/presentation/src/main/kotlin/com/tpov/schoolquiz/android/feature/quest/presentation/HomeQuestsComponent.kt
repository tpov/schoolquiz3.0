package com.tpov.schoolquiz.android.feature.quest.presentation

import com.tpov.schoolquiz.android.core.designsystem.model.CatalogDisplayItem
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import kotlinx.coroutines.flow.StateFlow

/**
 * Presentation contract for the "Квесты" (home) screen.
 *
 * Spec: docs/features/home-and-my-quests/06-api-contract.md §6.2
 * ADR-CMP-51: Decompose Component pattern.
 */
interface HomeQuestsComponent {
    val state: StateFlow<HomeQuestsUiState>

    /** TODO: future catalog detail navigation. */
    fun onCatalogClick(id: CatalogId)
}

/**
 * UI state snapshot for the home (catalog grid) screen.
 *
 * Spec: docs/features/home-and-my-quests/06-api-contract.md §6.2 HomeQuestsUiState
 */
data class HomeQuestsUiState(
    val catalogs: List<CatalogDisplayItem> = emptyList(),
    val isLoading: Boolean = false,
)
