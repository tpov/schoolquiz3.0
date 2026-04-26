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
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyListUiState
import com.tpov.schoolquiz.shared.feature.lesson.domain.repository.LessonRepository
import com.tpov.schoolquiz.shared.feature.theme.domain.model.ThemeId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class DefaultLessonListComponent(
    componentContext: ComponentContext,
    config: QuizzesConfig.LessonList,
    private val lessonRepository: LessonRepository,
    private val navigation: StackNavigation<QuizzesConfig>,
    coroutineContext: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ComponentContext by componentContext, LessonListComponent {

    private val componentJob = SupervisorJob()
    private val scope = CoroutineScope(componentJob + coroutineContext)

    private val themeId = ThemeId(config.themeId)
    override val titles: List<String> = config.titles

    private val _uiState = MutableValue<HierarchyListUiState>(HierarchyListUiState.Loading)
    override val uiState: Value<HierarchyListUiState> = _uiState

    init {
        scope.launch {
            lessonRepository.observeByTheme(themeId)
                .map { lessons ->
                    if (lessons.isEmpty()) HierarchyListUiState.Empty("Нет уроков")
                    else HierarchyListUiState.Loaded(lessons.map { it.toDrillItem() })
                }
                .catch { /* log */ }
                .collect { _uiState.value = it }
        }
        lifecycle.doOnDestroy { componentJob.cancel() }
    }

    override fun onLessonClick(lesson: HierarchyItemUi) {
        navigation.pushNew(
            QuizzesConfig.LessonPlaceholder(
                lessonId = lesson.id,
                lessonTitle = lesson.title,
                titles = titles + listOf(lesson.title),
            )
        )
    }
}
