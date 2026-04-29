package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.pushNew
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.LessonItemUi
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.LessonListUiState
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.AuthRepository
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.Lesson
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson.domain.repository.LessonRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonAttemptRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonAttemptStats
import com.tpov.schoolquiz.shared.feature.theme.domain.model.ThemeId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultLessonListComponent(
    componentContext: ComponentContext,
    config: QuizzesConfig.LessonList,
    private val lessonRepository: LessonRepository,
    private val attemptRepository: LessonAttemptRepository,
    private val authRepository: AuthRepository,
    private val navigation: StackNavigation<QuizzesConfig>,
    coroutineContext: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ComponentContext by componentContext, LessonListComponent {
    private val componentJob = SupervisorJob()
    private val scope = CoroutineScope(componentJob + coroutineContext)

    private val themeId = ThemeId(config.themeId)
    override val titles: List<String> = config.titles

    private val _uiState = MutableValue<LessonListUiState>(LessonListUiState.Loading)
    override val uiState: Value<LessonListUiState> = _uiState

    private val hardCheckedSet: MutableStateFlow<Set<String>> = MutableStateFlow(emptySet())

    init {
        scope.launch {
            combine(
                lessonRepository.observeByTheme(themeId),
                authRepository.observeUid().flatMapLatest { uid ->
                    if (uid == null) {
                        flowOf(emptyMap())
                    } else {
                        attemptRepository.observeAllStatsByUser(uid)
                    }
                },
                hardCheckedSet,
            ) { lessons, stats, checkedSet ->
                mapToUi(lessons, stats, checkedSet)
            }
                .catch { /* log */ }
                .collect { _uiState.value = it }
        }
        lifecycle.doOnDestroy {
            componentJob.cancel()
            hardCheckedSet.value = emptySet()
        }
    }

    override fun onLessonClick(lesson: LessonItemUi) {
        val mode = if (lesson.hardUnlocked && lesson.isHardChecked) Difficulty.HARD else Difficulty.EASY
        // AC-49: each new visit defaults to unchecked — clear before pushing runner.
        hardCheckedSet.update { it - lesson.id }
        navigation.pushNew(
            QuizzesConfig.LessonRunner(
                lessonId = lesson.id,
                mode = mode,
                titles = titles + lesson.title,
            ),
        )
    }

    override fun onHardCheckToggled(lessonId: String) {
        val item = (_uiState.value as? LessonListUiState.Loaded)?.items?.find { it.id == lessonId }
        if (item?.hardUnlocked == true) {
            hardCheckedSet.update { current ->
                if (lessonId in current) current - lessonId else current + lessonId
            }
        }
    }

    private fun mapToUi(
        lessons: List<Lesson>,
        stats: Map<LessonId, LessonAttemptStats>,
        checkedSet: Set<String>,
    ): LessonListUiState {
        if (lessons.isEmpty()) return LessonListUiState.Empty("Нет уроков")
        val items =
            lessons.map { lesson ->
                val lessonStats = stats[lesson.id]
                LessonItemUi(
                    id = lesson.id.value,
                    title = lesson.title,
                    orderLabel = "${lesson.order + 1}.",
                    bestStarsRawTenths = lessonStats?.bestStarsRawTenths ?: 0,
                    hardUnlocked = lessonStats?.hardUnlocked ?: false,
                    isHardChecked = lesson.id.value in checkedSet,
                )
            }
        return LessonListUiState.Loaded(items)
    }
}
