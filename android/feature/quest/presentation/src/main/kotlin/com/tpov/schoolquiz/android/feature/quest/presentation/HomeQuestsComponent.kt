package com.tpov.schoolquiz.android.feature.quest.presentation

import com.tpov.schoolquiz.android.core.designsystem.model.CatalogDisplayItem
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.feature.economy.domain.model.GiftBoxReward
import kotlinx.coroutines.flow.StateFlow

/**
 * Presentation contract for the "Квесты" (home) screen.
 *
 * Spec: docs/features/home-and-my-quests/06-api-contract.md §6.2
 * ADR-CMP-51: Decompose Component pattern.
 */
interface HomeQuestsComponent {
    val state: StateFlow<HomeQuestsUiState>

    fun onCatalogClick(
        id: CatalogId,
        name: String,
    )

    fun onGiftBoxFabClick() = Unit

    fun onGiftBoxDismiss() = Unit
}

/**
 * UI state snapshot for the home (catalog grid) screen.
 *
 * Spec: docs/features/home-and-my-quests/06-api-contract.md §6.2 HomeQuestsUiState
 */
data class HomeQuestsUiState(
    val catalogs: List<CatalogDisplayItem> = emptyList(),
    val isLoading: Boolean = false,
    val giftBoxCount: Int = 0,
    val giftBoxStreakDays: Int = 0,
    val giftBoxOpening: HomeGiftBoxOpeningState? = null,
)

sealed interface HomeGiftBoxOpeningState {
    data class Opening(
        val startedBoxCount: Int,
    ) : HomeGiftBoxOpeningState

    data class Opened(
        val reward: GiftBoxReward,
        val remainingBoxCount: Int,
        val profileSynced: Boolean,
    ) : HomeGiftBoxOpeningState

    data class Failed(
        val message: String,
        val remainingBoxCount: Int,
    ) : HomeGiftBoxOpeningState
}
