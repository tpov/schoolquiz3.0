package com.tpov.schoolquiz.android.feature.quest.presentation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.tpov.schoolquiz.android.core.designsystem.model.toDisplayItem
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.core.catalog.domain.use_case.ObserveCatalogsUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlin.coroutines.CoroutineContext

/**
 * Default implementation of [HomeQuestsComponent].
 *
 * Observes all non-archived catalogs via ObserveCatalogsUseCase (DAO WHERE archived=0 already
 * filters at the data layer). Maps to CatalogDisplayItem for UI consumption.
 *
 * Scope lifecycle tied to ComponentContext via doOnDestroy.
 *
 * Spec: docs/features/home-and-my-quests/06-api-contract.md §6.2 DefaultHomeQuestsComponent
 * ADR-CMP-51: Decompose Component pattern.
 */
class DefaultHomeQuestsComponent(
    componentContext: ComponentContext,
    private val observeCatalogs: ObserveCatalogsUseCase,
    private val onCatalogDrillDown: (CatalogId, String) -> Unit,
    mainContext: CoroutineContext = Dispatchers.Main.immediate,
) : HomeQuestsComponent, ComponentContext by componentContext {
    private val componentJob = SupervisorJob()
    private val scope = CoroutineScope(componentJob + mainContext)

    override val state =
        observeCatalogs()
            .map { catalogs ->
                HomeQuestsUiState(catalogs = catalogs.map { it.toDisplayItem() })
            }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = HomeQuestsUiState(),
            )

    init {
        lifecycle.doOnDestroy { componentJob.cancel() }
    }

    override fun onCatalogClick(
        id: CatalogId,
        name: String,
    ) {
        val catalogName = name.takeIf { it.isNotBlank() } ?: "Каталог"
        onCatalogDrillDown(id, catalogName)
    }
}
