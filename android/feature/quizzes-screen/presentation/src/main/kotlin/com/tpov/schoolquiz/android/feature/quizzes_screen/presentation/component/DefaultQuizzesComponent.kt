package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component

import androidx.compose.ui.graphics.vector.ImageVector
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.navigate
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.popTo
import com.arkivanov.decompose.router.stack.popToFirst
import com.arkivanov.decompose.router.stack.pushNew
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.backhandler.BackCallback
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.arkivanov.essenty.statekeeper.SerializableContainer
import com.tpov.schoolquiz.android.core.designsystem.components.resolveIcons
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ArenaRunnerComponentFactory
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ArenaRunnerRequest
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerComponentFactory
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerContentSyncCallbacks
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.PrivateQuizRunnerComponentFactory
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.PrivateQuizRunnerRequest
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.BreadcrumbRoot
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuestListMode
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.core.catalog.domain.model.QuestType
import com.tpov.schoolquiz.shared.core.catalog.domain.repository.CatalogRepository
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.AuthRepository
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.EconomyRepository
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson.domain.repository.LessonRepository
import com.tpov.schoolquiz.shared.feature.lesson.domain.repository.LessonUnlockQueue
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionStamp
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonAttemptRepository
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import com.tpov.schoolquiz.shared.feature.quest.domain.repository.QuestRepository
import com.tpov.schoolquiz.shared.feature.quest.domain.use_case.RetirePublicQuestUseCase
import com.tpov.schoolquiz.shared.feature.quest.domain.use_case.SetPublicQuestShelfUseCase
import com.tpov.schoolquiz.shared.feature.question.domain.repository.QuestionRepository
import com.tpov.schoolquiz.shared.feature.section.domain.repository.SectionRepository
import com.tpov.schoolquiz.shared.feature.theme.domain.repository.ThemeRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOf
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
    private val economyRepository: EconomyRepository,
    /** Очередь разблокировок фичи lesson — передаётся вниз в список уроков (история 5.3). */
    private val queueLessonUnlock: LessonUnlockQueue,
    private val questionRepository: QuestionRepository,
    private val catalogRepository: CatalogRepository,
    private val setPublicQuestShelf: SetPublicQuestShelfUseCase,
    private val retirePublicQuest: RetirePublicQuestUseCase,
    private val lessonRunnerFactory: LessonRunnerComponentFactory,
    private val privateQuizRunnerFactory: PrivateQuizRunnerComponentFactory,
    /** Приватная категория (AD-29): сохранённые приватные квизы владельца — строки списка. */
    private val observePrivateQuests: (String) -> Flow<List<com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.PrivateQuestItem>> = { flowOf(emptyList()) },
    private val arenaCatalogFactory: ArenaLessonCatalogComponentFactory,
    private val arenaRunnerFactory: ArenaRunnerComponentFactory,
    private val questContentSync: suspend (QuestId) -> Result<Unit> = { Result.success(Unit) },
    private val lessonContentSync: suspend (LessonId) -> Result<Unit> = { Result.success(Unit) },
    /** Цена открытия уроков — передаётся вниз в список уроков, где её показывает замок. */
    private val observeLessonUnlockPrices: (List<LessonId>) -> Flow<Map<LessonId, Int>> = { flowOf(emptyMap()) },
    private val mainContext: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val backgroundDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ComponentContext by componentContext, QuizzesComponent, ArenaRunNavigation {
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
                    ?.let(::restoreArenaReadOnlyRoute)
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
                    _currentCatalogIcons.value = resolved
                }
            }
        }
        lifecycle.doOnDestroy { componentScope.cancel() }
    }

    private fun updateCurrentCatalogId(config: QuizzesConfig) {
        when (config) {
            QuizzesConfig.Idle -> currentCatalogId.value = null
            QuizzesConfig.ArenaLessons -> currentCatalogId.value = null
            is QuizzesConfig.ArenaRunner -> currentCatalogId.value = null
            is QuizzesConfig.PublicQuestCatalogPicker -> currentCatalogId.value = null
            is QuizzesConfig.QuestList -> currentCatalogId.value = config.catalogId
            // deeper drill-down levels — keep the previously-set catalog id
            is QuizzesConfig.SectionList,
            is QuizzesConfig.ThemeList,
            is QuizzesConfig.LessonList,
            is QuizzesConfig.LessonRunner,
            is QuizzesConfig.PrivateQuizRunner,
            -> Unit
        }
    }

    private fun catalogNameFromConfig(config: QuizzesConfig): String? =
        when (config) {
            QuizzesConfig.Idle -> null
            QuizzesConfig.ArenaLessons -> null
            is QuizzesConfig.ArenaRunner -> null
            is QuizzesConfig.PrivateQuizRunner -> config.breadcrumbs.firstDynamicTitle()
            is QuizzesConfig.PublicQuestCatalogPicker -> null
            is QuizzesConfig.QuestList -> config.breadcrumbs.firstDynamicTitle()
            is QuizzesConfig.SectionList -> config.breadcrumbs.firstDynamicTitle()
            is QuizzesConfig.ThemeList -> config.breadcrumbs.firstDynamicTitle()
            is QuizzesConfig.LessonList -> config.breadcrumbs.firstDynamicTitle()
            is QuizzesConfig.LessonRunner -> config.breadcrumbs.firstDynamicTitle()
        }

    private fun List<BreadcrumbRoot>.firstDynamicTitle(): String? =
        filterIsInstance<BreadcrumbRoot.Dynamic>().firstOrNull()?.title?.takeIf { it.isNotBlank() }

    override fun openQuestList(
        catalogId: CatalogId,
        catalogName: String,
    ) {
        navigation.pushNew(
            QuizzesConfig.QuestList(
                catalogId.value,
                listOf(BreadcrumbRoot.Catalogs, BreadcrumbRoot.Dynamic(catalogName)),
            ),
        )
    }

    override fun openCourseArchive() {
        navigation.pushNew(
            QuizzesConfig.QuestList(
                catalogId = COURSES_CATALOG_ID,
                breadcrumbs = listOf(BreadcrumbRoot.Archive, BreadcrumbRoot.Courses),
                questType = QuestType.COURSE,
                shelf = ARCHIVE_SHELF,
                mode = QuestListMode.Archive,
            ),
        )
    }

    /** Compatibility entry name; the Arena now lists lessons, never a COURSE quest shelf. */
    override fun openCourseArena() {
        navigation.pushNew(QuizzesConfig.ArenaLessons)
    }

    override fun openPublicQuestCatalogPicker(targetShelf: String) {
        navigation.pushNew(
            QuizzesConfig.PublicQuestCatalogPicker(
                targetShelf = targetShelf,
                breadcrumbs = pickerBreadcrumbs(targetShelf),
            ),
        )
    }

    override fun openPublicQuestShelfCatalog(
        targetShelf: String,
        forcedHardMode: Boolean?,
    ) {
        navigation.pushNew(
            QuizzesConfig.PublicQuestCatalogPicker(
                targetShelf = targetShelf,
                breadcrumbs = pickerBreadcrumbs(targetShelf),
                selectionTargetShelf = null,
                forcedLessonMode = forcedHardMode?.let { if (it) Difficulty.HARD else Difficulty.EASY },
            ),
        )
    }

    /** Entry-point crumb is derived from the target shelf so no display text crosses the contract. */
    private fun pickerBreadcrumbs(targetShelf: String): List<BreadcrumbRoot> =
        listOf(
            when (targetShelf) {
                TOURNAMENT_SHELF -> BreadcrumbRoot.QualifierTournament
                TOURNAMENT_FINAL_SHELF -> BreadcrumbRoot.WorldChampionship
                else -> BreadcrumbRoot.HomeQuests
            },
            BreadcrumbRoot.Catalogs,
        )

    override fun openSectionList(
        questId: QuestId,
        breadcrumbs: List<BreadcrumbRoot>,
    ) {
        navigation.pushNew(QuizzesConfig.SectionList(questId.value, breadcrumbs))
    }

    override fun popToLevel(uiLevel: Int) {
        if (uiLevel < 0) return
        if (childStack.value.active.configuration is QuizzesConfig.ArenaRunner) {
            (childStack.value.active.instance as? QuizzesChild.LessonRunner)?.component?.onCrossButtonTap()
            return
        }
        val active = childStack.value.active
        val breadcrumbsSize =
            when (val cfg = active.configuration) {
                is QuizzesConfig.Idle -> 0
                QuizzesConfig.ArenaLessons -> 1
                is QuizzesConfig.ArenaRunner -> 1
                is QuizzesConfig.PrivateQuizRunner -> cfg.breadcrumbs.size
                is QuizzesConfig.PublicQuestCatalogPicker -> cfg.breadcrumbs.size
                is QuizzesConfig.QuestList -> cfg.breadcrumbs.size
                is QuizzesConfig.SectionList -> cfg.breadcrumbs.size
                is QuizzesConfig.ThemeList -> cfg.breadcrumbs.size
                is QuizzesConfig.LessonList -> cfg.breadcrumbs.size
                is QuizzesConfig.LessonRunner -> cfg.breadcrumbs.size
            }
        // In MyQuests entry path, SectionList is pushed directly (no QuestList in stack).
        // breadcrumbs may contain virtual "decoration" segments that have no stack entry.
        // virtualCount = how many leading breadcrumbs have no corresponding stack entry.
        val virtualCount = (breadcrumbsSize + 1 - childStack.value.items.size).coerceAtLeast(0)
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
        val route = childStack.value.active.configuration as? QuizzesConfig.ArenaRunner
        navigation.pop()
        val original = route?.initiatingSession ?: route?.fresh?.session
        val entry = (childStack.value.active.instance as? QuizzesChild.ArenaLessons)?.component?.entry
        if (original != null && entry?.state?.value?.session == original) {
            entry.onEvent(ArenaEntryEvent.Load(original, ArenaEntryPage.TICKETS))
        }
    }

    override fun openArenaRunner(
        session: ArenaSessionStamp,
        ticketId: String,
        fresh: Boolean,
    ) {
        val entry = (childStack.value.active.instance as? QuizzesChild.ArenaLessons)?.component?.entry?.state?.value
        val request = entry?.let { arenaRunRequest(it, session, ticketId, fresh) }
        if (request != null) {
            navigation.pushNew(
                QuizzesConfig.ArenaRunner(
                    request.ownerUid,
                    request.ticketId,
                    request.fresh,
                    request.initiatingSession,
                ),
            )
        }
    }

    override fun openLessonRunner(lessonId: String) {
        val active = childStack.value.active
        val config = active.configuration as? QuizzesConfig.LessonRunner ?: return
        // Replace, not push: the finished attempt has nowhere to go back to.
        navigation.navigate { stack -> stack.dropLast(1) + config.copy(lessonId = lessonId) }
    }

    override fun dismissQuizzes() {
        if (childStack.value.active.configuration is QuizzesConfig.ArenaRunner) {
            (childStack.value.active.instance as? QuizzesChild.LessonRunner)?.component?.onCrossButtonTap()
        } else {
            navigation.popToFirst()
        }
    }

    private fun createChild(
        config: QuizzesConfig,
        ctx: ComponentContext,
    ): QuizzesChild =
        when (config) {
            is QuizzesConfig.Idle ->
                QuizzesChild.Idle
            QuizzesConfig.ArenaLessons ->
                QuizzesChild.ArenaLessons(arenaCatalogFactory.create(ctx))
            is QuizzesConfig.PublicQuestCatalogPicker ->
                QuizzesChild.PublicQuestCatalogPicker(
                    DefaultPublicQuestCatalogPickerComponent(
                        componentContext = ctx,
                        config = config,
                        catalogRepository = catalogRepository,
                        navigation = navigation,
                        coroutineContext = mainContext,
                    ),
                )
            is QuizzesConfig.QuestList ->
                QuizzesChild.QuestList(
                    DefaultQuestListComponent(
                        componentContext = ctx,
                        config = config,
                        questRepository = questRepository,
                        sectionRepository = sectionRepository,
                        themeRepository = themeRepository,
                        lessonRepository = lessonRepository,
                        questionRepository = questionRepository,
                        authRepository = authRepository,
                        setPublicQuestShelf = setPublicQuestShelf,
                        retirePublicQuest = retirePublicQuest,
                        questContentSync = questContentSync,
                        observePrivateQuests = observePrivateQuests,
                        navigation = navigation,
                        coroutineContext = mainContext,
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
                        economyRepository,
                        queueLessonUnlock,
                        navigation,
                        observeUnlockPrices = observeLessonUnlockPrices,
                        lessonContentSync = lessonContentSync,
                        coroutineContext = mainContext,
                        backgroundDispatcher = backgroundDispatcher,
                    ),
                )
            is QuizzesConfig.LessonRunner -> {
                val lessonId = LessonId(config.lessonId)
                QuizzesChild.LessonRunner(
                    lessonRunnerFactory.create(
                        componentContext = ctx,
                        lessonId = lessonId,
                        mode = config.mode,
                        sessionMode = config.sessionMode,
                        contentSyncCallbacks =
                            LessonRunnerContentSyncCallbacks(
                                syncOnColdStart = { lessonContentSync(lessonId) },
                                refreshAfterWarmStart = {
                                    componentScope.launch(backgroundDispatcher) { lessonContentSync(lessonId) }
                                },
                            ),
                    ),
                )
            }
            is QuizzesConfig.ArenaRunner ->
                QuizzesChild.LessonRunner(
                    arenaRunnerFactory.create(
                        ctx,
                        ArenaRunnerRequest(
                            config.ownerUid,
                            config.ticketId,
                            config.fresh,
                            config.initiatingSession,
                        ),
                    ),
                )

            is QuizzesConfig.PrivateQuizRunner ->
                QuizzesChild.PrivateQuizRunner(
                    privateQuizRunnerFactory.create(
                        ctx,
                        PrivateQuizRunnerRequest(
                            ownerUid = config.ownerUid,
                            catalogId = config.catalogId,
                            questId = config.questId,
                            title = config.title,
                        ),
                    ),
                )
        }

    private fun isArchiveNavigationPath(config: QuizzesConfig): Boolean =
        when (config) {
            QuizzesConfig.Idle -> false
            QuizzesConfig.ArenaLessons -> false
            is QuizzesConfig.ArenaRunner -> false
            is QuizzesConfig.PrivateQuizRunner -> false
            is QuizzesConfig.PublicQuestCatalogPicker -> false
            is QuizzesConfig.QuestList -> config.mode == QuestListMode.Archive
            is QuizzesConfig.SectionList -> config.breadcrumbs.firstOrNull() == BreadcrumbRoot.Archive
            is QuizzesConfig.ThemeList -> config.breadcrumbs.firstOrNull() == BreadcrumbRoot.Archive
            is QuizzesConfig.LessonList -> config.breadcrumbs.firstOrNull() == BreadcrumbRoot.Archive
            is QuizzesConfig.LessonRunner -> config.breadcrumbs.firstOrNull() == BreadcrumbRoot.Archive
        }
}

private const val COURSES_CATALOG_ID = "courses"
private const val ARCHIVE_SHELF = "archive"
private const val ARENA_SHELF = "arena"
private const val TOURNAMENT_SHELF = "tournament"
private const val TOURNAMENT_FINAL_SHELF = "tournamentFinal"
private const val QUEST_LIST_STACK_INDEX = 1

/**
 * Migrate only the exact former player entry and its known drill-down tail. Curator Arena mode,
 * other catalogs and archive stacks are untouched. An unrecognizable legacy tail fails to Idle,
 * never to a guessed ordinary lesson or runner.
 */
private fun restoreArenaReadOnlyRoute(stack: List<QuizzesConfig>): List<QuizzesConfig> {
    // StateKeeper may retain an in-memory object rather than decoding JSON, so @Transient alone
    // cannot be the security boundary. Every restored Arena runner is ResumeOnly by construction.
    val restored = stack.map(QuizzesConfig::withoutArenaRunnerAuthority)
    val index = restored.indexOfFirst { it.isLegacyCourseArenaEntry() }
    if (index < 0) return restored
    val tail = restored.drop(index + 1)
    return if (tail.withIndex().all { (depth, config) -> config.isLegacyCourseArenaDescendant(depth) }) {
        restored.take(index) + QuizzesConfig.ArenaLessons
    } else {
        listOf(QuizzesConfig.Idle)
    }
}

private fun QuizzesConfig.withoutArenaRunnerAuthority(): QuizzesConfig =
    when (this) {
        is QuizzesConfig.ArenaRunner -> QuizzesConfig.ArenaRunner(ownerUid, ticketId)
        else -> this
    }

private fun QuizzesConfig.isLegacyCourseArenaEntry(): Boolean =
    this is QuizzesConfig.QuestList &&
        catalogId == COURSES_CATALOG_ID && questType == QuestType.COURSE && shelf == ARENA_SHELF &&
        mode == QuestListMode.Arena && selectionTargetShelf == null && forcedLessonMode == null &&
        breadcrumbs == listOf(BreadcrumbRoot.Arena, BreadcrumbRoot.Courses)

private fun QuizzesConfig.isLegacyCourseArenaDescendant(depth: Int): Boolean {
    val root = listOf(BreadcrumbRoot.Arena, BreadcrumbRoot.Courses)
    val breadcrumbs =
        when (this) {
            is QuizzesConfig.SectionList ->
                if (depth == SECTION_DEPTH && questType == QuestType.COURSE) breadcrumbs else null
            is QuizzesConfig.ThemeList ->
                if (depth == THEME_DEPTH && questType == QuestType.COURSE) breadcrumbs else null
            is QuizzesConfig.LessonList ->
                if (depth == LESSON_DEPTH && questType == QuestType.COURSE) breadcrumbs else null
            is QuizzesConfig.LessonRunner -> if (depth == RUNNER_DEPTH) breadcrumbs else null
            else -> null
        }
    return breadcrumbs?.take(root.size) == root
}

private const val SECTION_DEPTH = 0
private const val THEME_DEPTH = 1
private const val LESSON_DEPTH = 2
private const val RUNNER_DEPTH = 3
