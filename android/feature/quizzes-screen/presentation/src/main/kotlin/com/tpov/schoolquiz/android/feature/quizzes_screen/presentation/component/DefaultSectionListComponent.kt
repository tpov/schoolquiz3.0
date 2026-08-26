package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.pushNew
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.mapper.toDrillItem
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyItemUi
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyLevel
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyListUiState
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import com.tpov.schoolquiz.shared.feature.section.domain.repository.SectionRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class DefaultSectionListComponent(
    componentContext: ComponentContext,
    config: QuizzesConfig.SectionList,
    private val sectionRepository: SectionRepository,
    private val navigation: StackNavigation<QuizzesConfig>,
    coroutineContext: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ComponentContext by componentContext, SectionListComponent {
    private val componentJob = SupervisorJob()
    private val scope = CoroutineScope(componentJob + coroutineContext)

    private val questId = QuestId(config.questId)
    override val titles: List<String> = config.titles
    private val forcedLessonMode = config.forcedLessonMode

    private val _uiState = MutableValue<HierarchyListUiState>(HierarchyListUiState.Loading)
    override val uiState: Value<HierarchyListUiState> = _uiState

    init {
        scope.launch {
            sectionRepository.observeByQuest(questId)
                .map { sections ->
                    if (sections.isEmpty()) {
                        HierarchyListUiState.Empty(HierarchyLevel.SECTIONS)
                    } else {
                        HierarchyListUiState.Loaded(sections.sortedBy { it.order }.map { it.toDrillItem() })
                    }
                }
                .catch { /* log */ }
                .collect { _uiState.value = it }
        }
        lifecycle.doOnDestroy { componentJob.cancel() }
    }

    override fun onSectionClick(section: HierarchyItemUi) {
        navigation.pushNew(
            QuizzesConfig.ThemeList(
                sectionId = section.id,
                titles = titles + listOf(section.title),
                forcedLessonMode = forcedLessonMode,
            ),
        )
    }
}
