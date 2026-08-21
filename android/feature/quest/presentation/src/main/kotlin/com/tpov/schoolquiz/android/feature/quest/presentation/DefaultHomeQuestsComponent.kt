package com.tpov.schoolquiz.android.feature.quest.presentation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.tpov.schoolquiz.android.core.designsystem.model.toDisplayItem
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.core.catalog.domain.model.QuestType
import com.tpov.schoolquiz.shared.core.catalog.domain.use_case.ObserveCatalogsUseCase
import com.tpov.schoolquiz.shared.feature.economy.domain.use_case.OpenGiftBoxUseCase
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.use_case.ObserveCurrentProfileUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    private val observeProfile: ObserveCurrentProfileUseCase,
    private val openGiftBoxUseCase: OpenGiftBoxUseCase,
    private val onCatalogDrillDown: (CatalogId, String) -> Unit,
    mainContext: CoroutineContext = Dispatchers.Main.immediate,
) : HomeQuestsComponent, ComponentContext by componentContext {
    private val componentJob = SupervisorJob()
    private val scope = CoroutineScope(componentJob + mainContext)
    private val giftBoxOpening = MutableStateFlow<HomeGiftBoxOpeningState?>(null)

    override val state =
        combine(
            observeCatalogs(),
            observeProfile(),
            giftBoxOpening,
        ) { catalogs, profile, opening ->
            HomeQuestsUiState(
                catalogs =
                    catalogs
                        // Courses have their own entry point, so they are not listed here.
                        .filterNot { it.questType == QuestType.COURSE }
                        .map { it.toDisplayItem() },
                giftBoxCount = profile.boxCount,
                giftBoxStreakDays = profile.boxStreakDays,
                giftBoxOpening = opening,
            )
        }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = HomeQuestsUiState(),
            )

    init {
        lifecycle.doOnDestroy { componentJob.cancel() }
    }

    override fun onGiftBoxFabClick() {
        val current = state.value
        if (current.giftBoxCount <= 0 || current.giftBoxOpening is HomeGiftBoxOpeningState.Opening) return

        giftBoxOpening.value = HomeGiftBoxOpeningState.Opening(startedBoxCount = current.giftBoxCount)
        scope.launch {
            val result = openGiftBoxUseCase.execute()
            giftBoxOpening.update {
                result.fold(
                    onSuccess = { opening ->
                        val fallbackRemaining = (current.giftBoxCount - 1).coerceAtLeast(0)
                        HomeGiftBoxOpeningState.Opened(
                            reward = opening.reward,
                            remainingBoxCount = opening.remainingBoxCount ?: fallbackRemaining,
                            profileSynced = opening.profileSynced,
                        )
                    },
                    onFailure = { error ->
                        HomeGiftBoxOpeningState.Failed(
                            message = error.readableGiftBoxMessage(),
                            remainingBoxCount = current.giftBoxCount,
                        )
                    },
                )
            }
        }
    }

    override fun onGiftBoxDismiss() {
        giftBoxOpening.value = null
    }

    override fun onCatalogClick(
        id: CatalogId,
        name: String,
    ) {
        val catalogName = name.takeIf { it.isNotBlank() } ?: "Каталог"
        onCatalogDrillDown(id, catalogName)
    }

    private fun Throwable.readableGiftBoxMessage(): String {
        if (this is CancellationException) throw this
        val raw = message.orEmpty()
        return when {
            raw.contains("No gift boxes", ignoreCase = true) -> "Нет доступных коробок"
            raw.isNotBlank() -> raw
            else -> "Не удалось открыть коробку"
        }
    }
}
