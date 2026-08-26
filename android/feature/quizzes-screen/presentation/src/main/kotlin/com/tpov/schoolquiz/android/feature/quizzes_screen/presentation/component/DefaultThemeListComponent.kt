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
import com.tpov.schoolquiz.shared.feature.section.domain.model.SectionId
import com.tpov.schoolquiz.shared.feature.theme.domain.repository.ThemeRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class DefaultThemeListComponent(
    componentContext: ComponentContext,
    config: QuizzesConfig.ThemeList,
    private val themeRepository: ThemeRepository,
    private val navigation: StackNavigation<QuizzesConfig>,
    coroutineContext: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ComponentContext by componentContext, ThemeListComponent {
    private val componentJob = SupervisorJob()
    private val scope = CoroutineScope(componentJob + coroutineContext)

    private val sectionId = SectionId(config.sectionId)
    override val titles: List<String> = config.titles
    private val forcedLessonMode = config.forcedLessonMode

    private val _uiState = MutableValue<HierarchyListUiState>(HierarchyListUiState.Loading)
    override val uiState: Value<HierarchyListUiState> = _uiState

    init {
        scope.launch {
            themeRepository.observeBySection(sectionId)
                .map { themes ->
                    if (themes.isEmpty()) {
                        HierarchyListUiState.Empty(HierarchyLevel.THEMES)
                    } else {
                        HierarchyListUiState.Loaded(themes.sortedBy { it.order }.map { it.toDrillItem() })
                    }
                }
                .catch { /* log */ }
                .collect { _uiState.value = it }
        }
        lifecycle.doOnDestroy { componentJob.cancel() }
    }

    override fun onThemeClick(theme: HierarchyItemUi) {
        navigation.pushNew(
            QuizzesConfig.LessonList(
                themeId = theme.id,
                titles = titles + listOf(theme.title),
                forcedLessonMode = forcedLessonMode,
            ),
        )
    }
}
