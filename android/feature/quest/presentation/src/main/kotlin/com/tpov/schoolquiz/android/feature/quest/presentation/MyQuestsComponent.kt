package com.tpov.schoolquiz.android.feature.quest.presentation

import com.tpov.schoolquiz.android.core.designsystem.model.CatalogDisplayItem
import com.tpov.schoolquiz.android.core.designsystem.model.QuestDisplayItem
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import kotlinx.coroutines.flow.StateFlow

/**
 * Presentation contract for the "Мои квесты" screen.
 *
 * Spec: docs/features/home-and-my-quests/06-api-contract.md §6.1
 * ADR-CMP-51: Decompose Component pattern.
 */
interface MyQuestsComponent {
    val state: StateFlow<MyQuestsUiState>

    /** null = "Все категории" (no filter). */
    fun onCatalogSelected(id: CatalogId?)

    /** Navigate to create-quest screen (Destination.OpenQuestCreate). */
    fun onCreateQuestClick()

    /** Drill-down from MyQuests into SectionList for the tapped quest. */
    fun onQuestClick(quest: QuestDisplayItem)
}

/**
 * UI state snapshot for "Мои квесты" screen.
 *
 * isGuest=true → quests always empty, no repository call made.
 * Spec: docs/features/home-and-my-quests/06-api-contract.md §6.1 MyQuestsUiState
 */
data class MyQuestsUiState(
    val quests: List<QuestDisplayItem> = emptyList(),
    val catalogs: List<CatalogDisplayItem> = emptyList(),
    val selectedCatalogId: CatalogId? = null,
    val isGuest: Boolean = false,
    val isLoading: Boolean = false,
)
