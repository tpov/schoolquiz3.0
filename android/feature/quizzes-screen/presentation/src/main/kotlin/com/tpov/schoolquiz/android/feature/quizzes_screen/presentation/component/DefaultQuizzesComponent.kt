package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component

import androidx.compose.ui.graphics.vector.ImageVector
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.popTo
import com.arkivanov.decompose.router.stack.popToFirst
import com.arkivanov.decompose.router.stack.pushNew
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.backhandler.BackCallback
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.arkivanov.essenty.statekeeper.SerializableContainer
import com.tpov.schoolquiz.android.core.designsystem.components.resolveIcons
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerComponentFactory
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuestListMode
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.core.catalog.domain.repository.CatalogRepository
import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.AuthRepository
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson.domain.repository.LessonRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonAttemptRepository
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import com.tpov.schoolquiz.shared.feature.quest.domain.repository.QuestRepository
import com.tpov.schoolquiz.shared.feature.question.domain.repository.QuestionRepository
import com.tpov.schoolquiz.shared.feature.section.domain.repository.SectionRepository
import com.tpov.schoolquiz.shared.feature.theme.domain.repository.ThemeRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer

@Suppress("LongParameterList")
class DefaultQuizzesComponent(
    componentContext: ComponentContext,
    private val questRepository: QuestRepository,
    private val sectionRepository: SectionRepository,
    private val themeRepository: ThemeRepository,
    private val lessonRepository: LessonRepository,
    private val lessonAttemptRepository: LessonAttemptRepository,
    private val authRepository: AuthRepository,
    private val questionRepository: QuestionRepository,
    private val catalogRepository: CatalogRepository,
    private val lessonRunnerFactory: LessonRunnerComponentFactory,
    private val questContentSync: suspend (QuestId) -> Result<Unit> = { Result.success(Unit) },
    private val mainContext: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ComponentContext by componentContext, QuizzesComponent {
    private val navigation = StackNavigation<QuizzesConfig>()

    // saveStack/restoreStack overload: runCatching wraps SerializationException on corrupted
    // process-death state so the stack falls back to [Idle] instead of crashing on launch.
    // Spec: docs/features/quizzes-screen/plan/phase-03/overview.md Problem 6 / AC#21.
    override val childStack: Value<ChildStack<QuizzesConfig, QuizzesChild>> =
        childStack(
            source = navigation,
            initialStack = { listOf(QuizzesConfig.Idle) },
            saveStack = { stack ->
                SerializableContainer(
                    value = stack,
                    strategy = ListSerializer(QuizzesConfig.serializer()),
                )
            },
            restoreStack = { container ->
                runCatching {
                    container.consume(ListSerializer(QuizzesConfig.serializer()))
                }.getOrNull()
                    // AC 36: process kill = attempt lost. Pop runner back to LessonList on restart.
                    ?.filterNot { it is QuizzesConfig.LessonRunner }
                    ?.takeIf { it.isNotEmpty() }
            },
            handleBackButton = false,
            childFactory = ::createChild,
        )

    // priority = 100: PRIORITY_OVERLAY absent in Essenty 2.1.0 (ADR-QS-12, Pattern Invariant 5)
    // AC-3/AC-34: Back from active LessonRunner must route through runner's exit/abort flow.
    private val backCallback =
        BackCallback(priority = 100, isEnabled = false) {
            val activeChild = childStack.value.active.instance
            if (activeChild is QuizzesChild.LessonRunner) {
                activeChild.component.onCrossButtonTap()
            } else {
                navigation.pop()
            }
        }

    private val _currentCatalogName = MutableStateFlow<String?>(null)
    override val currentCatalogName: StateFlow<String?> = _currentCatalogName.asStateFlow()

    private val _currentCatalogIcons: MutableStateFlow<List<ImageVector>> =
        MutableStateFlow(emptyList())
    override val currentCatalogIcons: StateFlow<List<ImageVector>> = _currentCatalogIcons.asStateFlow()

    private val componentScope = CoroutineScope(SupervisorJob() + mainContext)

    /**
     * Catalog id whose icons are currently displayed. Updated on entry to QuestList;
     * preserved through deeper drill-down levels; cleared on Idle.
     */
    private val currentCatalogId: MutableStateFlow<String?> = MutableStateFlow(null)

    init {
        backHandler.register(backCallback)
        childStack.subscribe { stack ->
            backCallback.isEnabled = stack.backStack.isNotEmpty()
            _currentCatalogName.value = catalogNameFromConfig(stack.active.configuration)
            updateCurrentCatalogId(stack.active.configuration)
        }
        // Re-resolve icons whenever the active catalog id OR the catalog list changes
        // (the latter fires after a successful refreshFromRemote).
        componentScope.launch {
            currentCatalogId.collectLatest { id ->
                if (id == null) {
                    _currentCatalogIcons.value = emptyList()
                    return@collectLatest
                }
                catalogRepository.observeAll().collect { catalogs ->
                    val catalog = catalogs.firstOrNull { it.id.value == id }
                    val resolved = resolveIcons(catalog?.iconNames ?: emptyList())
                    android.util.Log.d(
                        "QuizzesIcons",
                        "catalogId=$id name=${catalog?.name} " +
                            "rawIconNames=${catalog?.iconNames} resolvedCount=${resolved.size}",
                    )
                    _currentCatalogIcons.value = resolved
                }
            }
        }
        lifecycle.doOnDestroy { componentScope.cancel() }
    }

    private fun updateCurrentCatalogId(config: QuizzesConfig) {
        when (config) {
            QuizzesConfig.Idle -> currentCatalogId.value = null
            is QuizzesConfig.QuestList -> currentCatalogId.value = config.catalogId
            // deeper drill-down levels — keep the previously-set catalog id
            is QuizzesConfig.SectionList,
            is QuizzesConfig.ThemeList,
            is QuizzesConfig.LessonList,
            is QuizzesConfig.LessonRunner,
            -> Unit
        }
    }

    private fun catalogNameFromConfig(config: QuizzesConfig): String? =
        when (config) {
            is QuizzesConfig.QuestList -> config.titles.getOrNull(1)
            is QuizzesConfig.SectionList -> config.titles.getOrNull(1)
            is QuizzesConfig.ThemeList -> config.titles.getOrNull(1)
            is QuizzesConfig.LessonList -> config.titles.getOrNull(1)
            is QuizzesConfig.LessonRunner -> config.titles.getOrNull(1)
            QuizzesConfig.Idle -> null
        }

    override fun openQuestList(
        catalogId: CatalogId,
        catalogName: String,
    ) {
        navigation.pushNew(QuizzesConfig.QuestList(catalogId.value, listOf("Каталоги", catalogName)))
    }

    override fun openCourseArchive() {
        navigation.pushNew(
            QuizzesConfig.QuestList(
                catalogId = COURSES_CATALOG_ID,
                titles = listOf("Архив", "Курсы"),
                shelf = ARCHIVE_SHELF,
                mode = QuestListMode.Archive,
            ),
        )
    }

    override fun openSectionList(
        questId: QuestId,
        titles: List<String>,
    ) {
        navigation.pushNew(QuizzesConfig.SectionList(questId.value, titles))
    }

    override fun popToLevel(uiLevel: Int) {
        if (uiLevel < 0) return
        val active = childStack.value.active
        val titlesSize =
            when (val cfg = active.configuration) {
                is QuizzesConfig.Idle -> 0
                is QuizzesConfig.QuestList -> cfg.titles.size
                is QuizzesConfig.SectionList -> cfg.titles.size
                is QuizzesConfig.ThemeList -> cfg.titles.size
                is QuizzesConfig.LessonList -> cfg.titles.size
                is QuizzesConfig.LessonRunner -> cfg.titles.size
            }
        // In MyQuests entry path, SectionList is pushed directly (no QuestList in stack).
        // titles may contain virtual "decoration" segments that have no stack entry.
        // virtualCount = how many leading titles have no corresponding stack entry.
        val virtualCount = (titlesSize + 1 - childStack.value.items.size).coerceAtLeast(0)
        val adjustedLevel = uiLevel - virtualCount
        if (adjustedLevel < 0) {
            if (isArchiveNavigationPath(active.configuration)) {
                navigation.popTo(QUEST_LIST_STACK_INDEX)
            } else {
                // Clicked a virtual breadcrumb (e.g., catalog name from MyQuests path) — dismiss overlay
                navigation.popToFirst()
            }
        } else {
            navigation.popTo(adjustedLevel + 1)
        }
    }

    override fun popCurrentChild() {
        navigation.pop()
    }

    override fun dismissQuizzes() {
        navigation.popToFirst()
    }

    private fun createChild(
        config: QuizzesConfig,
        ctx: ComponentContext,
    ): QuizzesChild =
        when (config) {
            is QuizzesConfig.Idle ->
                QuizzesChild.Idle
            is QuizzesConfig.QuestList ->
                QuizzesChild.QuestList(
                    DefaultQuestListComponent(
                        ctx,
                        config,
                        questRepository,
                        sectionRepository,
                        themeRepository,
                        lessonRepository,
                        questionRepository,
                        questContentSync,
                        navigation,
                        mainContext,
                    ),
                )
            is QuizzesConfig.SectionList ->
                QuizzesChild.SectionList(
                    DefaultSectionListComponent(ctx, config, sectionRepository, navigation, mainContext),
                )
            is QuizzesConfig.ThemeList ->
                QuizzesChild.ThemeList(
                    DefaultThemeListComponent(ctx, config, themeRepository, navigation, mainContext),
                )
            is QuizzesConfig.LessonList ->
                QuizzesChild.LessonList(
                    DefaultLessonListComponent(
                        ctx,
                        config,
                        lessonRepository,
                        lessonAttemptRepository,
                        authRepository,
                        navigation,
                        mainContext,
                    ),
                )
            is QuizzesConfig.LessonRunner ->
                QuizzesChild.LessonRunner(
                    lessonRunnerFactory.create(ctx, LessonId(config.lessonId), config.mode),
                )
        }

    private fun isArchiveNavigationPath(config: QuizzesConfig): Boolean =
        when (config) {
            QuizzesConfig.Idle -> false
            is QuizzesConfig.QuestList -> config.mode == QuestListMode.Archive
            is QuizzesConfig.SectionList -> config.titles.firstOrNull() == ARCHIVE_ENTRY_TITLE
            is QuizzesConfig.ThemeList -> config.titles.firstOrNull() == ARCHIVE_ENTRY_TITLE
            is QuizzesConfig.LessonList -> config.titles.firstOrNull() == ARCHIVE_ENTRY_TITLE
            is QuizzesConfig.LessonRunner -> config.titles.firstOrNull() == ARCHIVE_ENTRY_TITLE
        }
}

private const val COURSES_CATALOG_ID = "courses"
private const val ARCHIVE_SHELF = "archive"
private const val ARCHIVE_ENTRY_TITLE = "Архив"
private const val QUEST_LIST_STACK_INDEX = 1
