package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.pushNew
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.tpov.schoolquiz.android.core.designsystem.model.QuestDisplayItem
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.mapper.toQuestDisplayItem
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.QuestListUiState
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.feature.quest.domain.repository.QuestRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class DefaultQuestListComponent(
    componentContext: ComponentContext,
    config: QuizzesConfig.QuestList,
    private val questRepository: QuestRepository,
    private val navigation: StackNavigation<QuizzesConfig>,
    coroutineContext: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ComponentContext by componentContext, QuestListComponent {
    private val componentJob = SupervisorJob()
    private val scope = CoroutineScope(componentJob + coroutineContext)

    private val catalogId = CatalogId(config.catalogId)
    override val titles: List<String> = config.titles

    private val _uiState = MutableValue<QuestListUiState>(QuestListUiState.Loading)
    override val uiState: Value<QuestListUiState> = _uiState

    init {
        scope.launch {
            questRepository.observeByCatalog(catalogId, "home")
                .map { quests ->
                    if (quests.isEmpty()) {
                        QuestListUiState.Empty
                    } else {
                        QuestListUiState.Loaded(quests.map { it.toQuestDisplayItem() })
                    }
                }
                .catch { /* log */ }
                .collect { _uiState.value = it }
        }
        lifecycle.doOnDestroy { componentJob.cancel() }
    }

    override fun onQuestClick(quest: QuestDisplayItem) {
        navigation.pushNew(
            QuizzesConfig.SectionList(
                questId = quest.id.value,
                titles = titles + listOf(quest.title),
            ),
        )
    }

    override fun onShareClick(quest: QuestDisplayItem) {
        // Intent dispatched from QuestListScreen (ADR-QS-08)
    }
}
