package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.pushNew
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.tpov.schoolquiz.android.core.designsystem.model.toDisplayItem
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.BreadcrumbRoot
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuestListMode
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.PublicQuestCatalogPickerUiState
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.core.catalog.domain.repository.CatalogRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class DefaultPublicQuestCatalogPickerComponent(
    componentContext: ComponentContext,
    private val config: QuizzesConfig.PublicQuestCatalogPicker,
    catalogRepository: CatalogRepository,
    private val navigation: StackNavigation<QuizzesConfig>,
    coroutineContext: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ComponentContext by componentContext, PublicQuestCatalogPickerComponent {
    private val componentJob = SupervisorJob()
    private val scope = CoroutineScope(componentJob + coroutineContext)

    override val breadcrumbs: List<BreadcrumbRoot> = config.breadcrumbs

    override val state =
        catalogRepository.observeAll()
            .map { catalogs ->
                val displayItems =
                    catalogs
                        .filter { catalog -> catalog.id.isAllowedFor(config.targetShelf) }
                        .map { catalog -> catalog.toDisplayItem() }
                if (displayItems.isEmpty()) {
                    PublicQuestCatalogPickerUiState.Empty
                } else {
                    PublicQuestCatalogPickerUiState.Loaded(displayItems)
                }
            }
            .catch { emit(PublicQuestCatalogPickerUiState.Empty) }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = PublicQuestCatalogPickerUiState.Loading,
            )

    init {
        lifecycle.doOnDestroy { componentJob.cancel() }
    }

    override fun onCatalogClick(
        id: CatalogId,
        name: String,
    ) {
        val selectionTargetShelf = config.selectionTargetShelf
        navigation.pushNew(
            QuizzesConfig.QuestList(
                catalogId = id.value,
                // Blank names are forwarded as-is; the screen resolves them to a localized fallback.
                breadcrumbs = breadcrumbs + BreadcrumbRoot.Dynamic(name),
                shelf = if (selectionTargetShelf == null) config.targetShelf else ARENA_SHELF,
                mode = QuestListMode.Arena,
                selectionTargetShelf = selectionTargetShelf,
                forcedLessonMode = config.forcedLessonMode,
            ),
        )
    }

    private fun CatalogId.isAllowedFor(targetShelf: String): Boolean =
        when (targetShelf) {
            TOURNAMENT_SHELF,
            TOURNAMENT_FINAL_SHELF,
            -> value in TOURNAMENT_CATALOG_IDS
            else -> true
        }

    private companion object {
        const val ARENA_SHELF = "arena"
        const val TOURNAMENT_SHELF = "tournament"
        const val TOURNAMENT_FINAL_SHELF = "tournamentFinal"
        val TOURNAMENT_CATALOG_IDS = setOf("school", "games")
    }
}
