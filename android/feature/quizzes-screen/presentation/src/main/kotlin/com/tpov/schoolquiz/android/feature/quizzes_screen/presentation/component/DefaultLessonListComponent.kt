package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.pushNew
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.BreadcrumbRoot
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyLevel
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.LessonItemUi
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.LessonListUiState
import com.tpov.schoolquiz.shared.core.catalog.domain.model.QuestType
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.AuthRepository
import com.tpov.schoolquiz.shared.feature.economy.domain.model.LessonUnlockKind
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.EconomyRepository
import com.tpov.schoolquiz.shared.feature.lesson.domain.logic.LessonAccess
import com.tpov.schoolquiz.shared.feature.lesson.domain.logic.resolveLessonAccess
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Suppress("LongParameterList")
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultLessonListComponent(
    componentContext: ComponentContext,
    config: QuizzesConfig.LessonList,
    private val lessonRepository: LessonRepository,
    private val attemptRepository: LessonAttemptRepository,
    private val authRepository: AuthRepository,
    private val economyRepository: EconomyRepository,
    private val navigation: StackNavigation<QuizzesConfig>,
    private val lessonContentSync: suspend (LessonId) -> Result<Unit> = { Result.success(Unit) },
    coroutineContext: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ComponentContext by componentContext, LessonListComponent {
    private val componentJob = SupervisorJob()
    private val scope = CoroutineScope(componentJob + coroutineContext)

    private val themeId = ThemeId(config.themeId)
    override val breadcrumbs: List<BreadcrumbRoot> = config.breadcrumbs
    private val forcedLessonMode = config.forcedLessonMode

    // Only a course teaches in a fixed order. Everywhere else every lesson stays open.
    private val gatesSequentially = config.questType == QuestType.COURSE

    private val _uiState = MutableValue<LessonListUiState>(LessonListUiState.Loading)
    override val uiState: Value<LessonListUiState> = _uiState

    private val hardCheckedSet: MutableStateFlow<Set<String>> = MutableStateFlow(emptySet())

    private val _messages = Channel<String>(Channel.BUFFERED)
    override val messages: Flow<String> = _messages.receiveAsFlow()

    /** Lessons with a purchase in flight, so a second tap cannot start a second charge. */
    private val purchasing = MutableStateFlow<Set<String>>(emptySet())

    init {
        val statsFlow =
            authRepository.observeUid().flatMapLatest { uid ->
                if (uid == null) {
                    flowOf(emptyMap())
                } else {
                    attemptRepository.observeAllStatsByUser(uid)
                }
            }
        val unlocksFlow = economyRepository.observeBalance().map { it.lessonUnlocks }
        scope.launch {
            lessonRepository.observeByTheme(themeId).flatMapLatest { lessons ->
                combine(
                    statsFlow,
                    hardCheckedSet,
                    unlocksFlow,
                ) { stats, checkedSet, unlocks ->
                    mapToUi(
                        lessons = lessons,
                        stats = stats,
                        checkedSet = checkedSet,
                        unlocks = unlocks,
                    )
                }
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
        // A shut lesson does not open by being tapped; the tap is a request to buy it.
        if (lesson.access == LessonAccess.LOCKED) {
            onUnlockClick(lesson)
            return
        }
        val mode =
            forcedLessonMode ?: if (lesson.hardUnlocked && lesson.isHardChecked) {
                Difficulty.HARD
            } else {
                Difficulty.EASY
            }
        // AC-49: each new visit defaults to unchecked — clear before pushing runner.
        hardCheckedSet.update { it - lesson.id }
        scope.launch {
            lessonContentSync(LessonId(lesson.id))
            navigation.pushNew(
                QuizzesConfig.LessonRunner(
                    lessonId = lesson.id,
                    mode = mode,
                    breadcrumbs = breadcrumbs + BreadcrumbRoot.Dynamic(lesson.title),
                ),
            )
        }
    }

    override fun onUnlockClick(lesson: LessonItemUi) {
        // Buying is a server call: nolics live in the profile, so a local deduction would be erased
        // by the next sync, and the price is the server's to decide.
        if (lesson.access != LessonAccess.LOCKED) return
        // Two taps used to mean two charges, and the older of the two answers would then overwrite
        // the newer set of unlocks — paying for a lesson that stayed shut.
        if (!purchasing.compareAndSetAdding(lesson.id)) return
        scope.launch {
            val result = economyRepository.unlockLesson(lesson.id, LessonUnlockKind.LESSON)
            purchasing.update { it - lesson.id }
            // A refused purchase leaves the row locked, which says nothing about why. Silence here
            // reads as a dead button.
            result.exceptionOrNull()?.let { error ->
                _messages.trySend(error.message?.takeIf { it.isNotBlank() } ?: "Не удалось открыть урок")
            }
        }
    }

    /** Adds [lessonId] and reports whether it was absent — a compare-and-set on the in-flight set. */
    private fun MutableStateFlow<Set<String>>.compareAndSetAdding(lessonId: String): Boolean {
        while (true) {
            val current = value
            if (lessonId in current) return false
            if (compareAndSet(current, current + lessonId)) return true
        }
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
        unlocks: Set<String>,
    ): LessonListUiState {
        if (lessons.isEmpty()) return LessonListUiState.Empty(HierarchyLevel.LESSONS)
        val ordered = lessons.sortedBy { it.order }
        val access =
            if (gatesSequentially) {
                resolveLessonAccess(
                    orderedLessonIds = ordered.map { it.id },
                    // hardUnlocked is the all-easy-correct predicate, which is what "passed" means.
                    passed = stats.filterValues { it.hardUnlocked }.keys,
                    purchased =
                        ordered
                            .map { it.id }
                            .filterTo(mutableSetOf()) { LessonUnlockKind.LESSON.keyFor(it.value) in unlocks },
                )
            } else {
                emptyMap()
            }
        val items =
            ordered.map { lesson ->
                val lessonStats = stats[lesson.id]
                LessonItemUi(
                    id = lesson.id.value,
                    title = lesson.title,
                    orderLabel = "${lesson.order + 1}.",
                    averageRating = lesson.averageRating,
                    ratingCount = lesson.ratingCount,
                    bestStarsRawTenths = lessonStats?.bestStarsRawTenths ?: 0,
                    hardUnlocked = lessonStats?.hardUnlocked ?: false,
                    isHardChecked = lesson.id.value in checkedSet,
                    access = access[lesson.id] ?: LessonAccess.OPEN,
                )
            }
        return LessonListUiState.Loaded(items)
    }
}
