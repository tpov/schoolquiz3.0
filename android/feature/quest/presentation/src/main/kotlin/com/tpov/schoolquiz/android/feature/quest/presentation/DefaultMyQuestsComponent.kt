package com.tpov.schoolquiz.android.feature.quest.presentation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.instancekeeper.InstanceKeeper
import com.arkivanov.essenty.instancekeeper.getOrCreate
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.tpov.schoolquiz.android.core.designsystem.model.QuestDisplayItem
import com.tpov.schoolquiz.android.core.designsystem.model.toDisplayItem
import com.tpov.schoolquiz.android.feature.quest.presentation.mapper.toDisplayItem
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.core.catalog.domain.use_case.ObserveCatalogsUseCase
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Destination
import com.tpov.schoolquiz.shared.feature.app_shell.domain.navigation.Navigator
import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.AuthRepository
import com.tpov.schoolquiz.shared.feature.quest.domain.use_case.ObserveMyQuestsUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlin.coroutines.CoroutineContext

/**
 * Default implementation of [MyQuestsComponent].
 *
 * Uses flatMapLatest on authRepo.observeUid() — safe for mid-session login/logout:
 *   uid=null  → flowOf(emptyList()), isGuest=true, observeMyQuests NOT called (AC#45)
 *   uid!=null → observeMyQuests(uid, catalogFilter)
 *
 * selectedCatalog is backed by InstanceKeeper (ADR-CMP-51) — survives config changes (rotation).
 * Scope is tied to ComponentContext lifecycle via doOnDestroy — cancelled on destroy.
 * mainContext is a constructor param for testability (default: Dispatchers.Main.immediate).
 *
 * Spec: docs/features/home-and-my-quests/06-api-contract.md §6.1 DefaultMyQuestsComponent
 * ADR-CMP-51: Decompose Component pattern + instanceKeeper retention.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultMyQuestsComponent(
    componentContext: ComponentContext,
    private val authRepo: AuthRepository,
    private val observeMyQuests: ObserveMyQuestsUseCase,
    private val observeCatalogs: ObserveCatalogsUseCase,
    private val navigator: Navigator,
    private val onQuestDrillDown: (QuestDisplayItem) -> Unit = {},
    mainContext: CoroutineContext = Dispatchers.Main.immediate,
) : MyQuestsComponent, ComponentContext by componentContext {
    private val componentJob = SupervisorJob()
    private val scope = CoroutineScope(componentJob + mainContext)

    // InstanceKeeper retains selectedCatalog across config changes (rotation).
    // ADR-CMP-51: stateful UI state backed by instanceKeeper, not plain MutableStateFlow.
    private val selectedCatalogHolder: SelectedCatalogHolder =
        instanceKeeper.getOrCreate { SelectedCatalogHolder() }
    private val selectedCatalog: MutableStateFlow<CatalogId?> = selectedCatalogHolder.flow

    private val uidFlow = authRepo.observeUid()

    private val questsFlow =
        uidFlow.flatMapLatest { uid ->
            if (uid == null) {
                selectedCatalog.value = null
                flowOf(emptyList())
            } else {
                selectedCatalog.flatMapLatest { catalogId ->
                    observeMyQuests(uid, catalogId)
                }
            }
        }

    private val isGuestFlow = uidFlow.map { it == null }

    // Catalogs are public data; no uid gating required.
    private val catalogsFlow = observeCatalogs()

    override val state =
        combine(
            questsFlow,
            catalogsFlow,
            selectedCatalog,
            isGuestFlow,
        ) { quests, catalogs, selectedId, isGuest ->
            MyQuestsUiState(
                quests = quests.map { it.toDisplayItem() },
                catalogs = catalogs.map { it.toDisplayItem() },
                selectedCatalogId = selectedId,
                isGuest = isGuest,
                isLoading = false,
            )
        }.stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = MyQuestsUiState(),
        )

    init {
        lifecycle.doOnDestroy { componentJob.cancel() }
    }

    override fun onCatalogSelected(id: CatalogId?) {
        selectedCatalog.value = id
    }

    // AC#46 / Journey 11: guest may tap FAB — navigates to OpenQuestCreate → UnderConstructionScreen.
    // No auth check here — UnderConstructionScreen is placeholder, no actual quest creation.
    // Future create-quest feature MUST re-validate auth before allowing quest submission.
    override fun onCreateQuestClick() {
        navigator.goTo(Destination.OpenQuestCreate)
    }

    override fun onQuestClick(quest: QuestDisplayItem) {
        onQuestDrillDown(quest)
    }

    private class SelectedCatalogHolder : InstanceKeeper.Instance {
        val flow = MutableStateFlow<CatalogId?>(null)

        override fun onDestroy() = Unit
    }
}
