package com.tpov.schoolquiz.apps.android_next

import com.arkivanov.decompose.Child
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.lifecycle.stop
import com.tpov.schoolquiz.android.core.designsystem.model.QuestDisplayItem
import com.tpov.schoolquiz.android.feature.app_shell.presentation.component.DefaultRootComponent
import com.tpov.schoolquiz.android.feature.app_shell.presentation.di.appShellPresentationModule
import com.tpov.schoolquiz.android.feature.quest.presentation.HomeQuestsComponent
import com.tpov.schoolquiz.android.feature.quest.presentation.HomeQuestsUiState
import com.tpov.schoolquiz.android.feature.quest.presentation.MyQuestsComponent
import com.tpov.schoolquiz.android.feature.quest.presentation.MyQuestsUiState
import com.tpov.schoolquiz.android.feature.quest.presentation.di.questPresentationModule
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesChild
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesComponent
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.di.quizzesPresentationModule
import com.tpov.schoolquiz.shared.core.catalog.domain.di.catalogDomainModule
import com.tpov.schoolquiz.shared.core.catalog.domain.model.Catalog
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.core.catalog.domain.repository.CatalogRepository
import com.tpov.schoolquiz.shared.core.persistence.UserStatsDao
import com.tpov.schoolquiz.shared.core.persistence.UserStatsEntity
import com.tpov.schoolquiz.shared.core.stats.RawUserStats
import com.tpov.schoolquiz.shared.core.stats.UserStatsDataSource
import com.tpov.schoolquiz.shared.feature.app_shell.data.AuthRepositoryImpl
import com.tpov.schoolquiz.shared.feature.app_shell.data.UserStatsRepositoryImpl
import com.tpov.schoolquiz.shared.feature.app_shell.data.di.appShellDataModule
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.DeepLink
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Destination
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.RootEvent
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.UserStats
import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.AuthRepository
import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.UserStatsRepository
import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.InitializeAppShellUseCase
import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.NavigateUseCase
import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.ObserveAppShellStateUseCase
import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.OnTabRetapUseCase
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.Lesson
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson.domain.repository.LessonRepository
import com.tpov.schoolquiz.shared.feature.quest.domain.di.questDomainModule
import com.tpov.schoolquiz.shared.feature.quest.domain.model.Quest
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import com.tpov.schoolquiz.shared.feature.quest.domain.repository.QuestRepository
import com.tpov.schoolquiz.shared.feature.section.domain.model.Section
import com.tpov.schoolquiz.shared.feature.section.domain.model.SectionId
import com.tpov.schoolquiz.shared.feature.section.domain.repository.SectionRepository
import com.tpov.schoolquiz.shared.feature.theme.domain.model.Theme
import com.tpov.schoolquiz.shared.feature.theme.domain.model.ThemeId
import com.tpov.schoolquiz.shared.feature.theme.domain.repository.ThemeRepository
import androidx.work.WorkManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.mockito.Mockito.mock
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Smoke tests: Koin module graph resolves without errors.
 *
 * D2 coverage: firebaseModule replaced by testDataSourceModule + appShellDataModule.
 * H5 fix (phase-01): appShellPresentationModule created in phase-04.
 * Phase-07: full-stack wiring (all 3 modules) verified here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KoinModuleWiringTest : KoinTest {

    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = UnconfinedTestDispatcher(testScheduler)

    private val testDataSourceModule = module {
        single<UserStatsDataSource> {
            object : UserStatsDataSource {
                override fun observeRaw() = emptyFlow<RawUserStats>()
                override suspend fun fetchRaw() = RawUserStats()
            }
        }
    }

    private val testDaoModule = module {
        single<UserStatsDao> {
            object : UserStatsDao {
                override fun observeByUid(uid: String) = emptyFlow<UserStatsEntity?>()
                override suspend fun findByUid(uid: String): UserStatsEntity? = null
                override suspend fun upsert(entity: UserStatsEntity) {}
                override suspend fun updateDeveloperLevel(uid: String, value: Int) {}
            }
        }
    }

    private val testWorkManagerModule = module {
        single<WorkManager> { mock(WorkManager::class.java) }
    }

    private val testRepositoryStubsModule = module {
        single<QuestRepository> {
            object : QuestRepository {
                override fun observeMyQuests(authorUid: String, catalogId: CatalogId?) =
                    flowOf(emptyList<Quest>())
                override fun observeByCatalog(catalogId: CatalogId, shelf: String) =
                    flowOf(emptyList<Quest>())
                override fun observeByShelf(shelf: String) = flowOf(emptyList<Quest>())
                override suspend fun getById(id: QuestId): Quest? = null
                override suspend fun refreshFromRemote(
                    currentUserUid: String?,
                    availableShelves: Set<String>,
                    catalogIdsToSync: Set<CatalogId>,
                    cursor: Long,
                ): Result<Set<QuestId>> = Result.success(emptySet())
            }
        }
        single<CatalogRepository> {
            object : CatalogRepository {
                override fun observeAll() = flowOf(emptyList<Catalog>())
                override suspend fun refreshFromRemote(): Result<Set<CatalogId>> =
                    Result.success(emptySet())
                override suspend fun getById(id: CatalogId): Catalog? = null
            }
        }
        single<SectionRepository> {
            object : SectionRepository {
                override fun observeByQuest(questId: QuestId) = flowOf(emptyList<Section>())
                override suspend fun getById(id: SectionId): Section? = null
                override suspend fun refreshByParents(
                    questIds: Set<QuestId>,
                    cursor: Long,
                ): Result<Set<SectionId>> = Result.success(emptySet())
                override suspend fun getLocalContentsVersion(id: SectionId): Long? = null
            }
        }
        single<ThemeRepository> {
            object : ThemeRepository {
                override fun observeBySection(sectionId: SectionId) = flowOf(emptyList<Theme>())
                override suspend fun getById(id: ThemeId): Theme? = null
                override suspend fun refreshByParents(
                    sectionIds: Set<SectionId>,
                    cursor: Long,
                ): Result<Set<ThemeId>> = Result.success(emptySet())
                override suspend fun getLocalContentsVersion(id: ThemeId): Long? = null
            }
        }
        single<LessonRepository> {
            object : LessonRepository {
                override fun observeByTheme(themeId: ThemeId) = flowOf(emptyList<Lesson>())
                override suspend fun getById(id: LessonId): Lesson? = null
                override suspend fun refreshByParents(
                    themeIds: Set<ThemeId>,
                    cursor: Long,
                ): Result<Set<LessonId>> = Result.success(emptySet())
                override suspend fun getLocalContentsVersion(id: LessonId): Long? = null
            }
        }
    }

    @Before
    fun setUp() {
        kotlinx.coroutines.Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        stopKoin()
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    // -----------------------------------------------------------------------
    // Phase-01 D2: appShellDataModule resolves UserStatsRepository
    // -----------------------------------------------------------------------

    @Test
    fun `D2 appShellDataModule resolves UserStatsRepository given UserStatsDataSource`() {
        startKoin {
            modules(testDataSourceModule, testDaoModule, appShellDataModule())
        }

        val repo = getKoin().get<UserStatsRepository>()
        assertNotNull(repo)
    }

    @Test
    fun `D2 UserStatsRepository resolves as UserStatsRepositoryImpl`() {
        startKoin {
            modules(testDataSourceModule, testDaoModule, appShellDataModule())
        }

        val repo = getKoin().get<UserStatsRepository>()
        assertNotNull(repo)
        assertTrue(repo is UserStatsRepositoryImpl)
    }

    // -----------------------------------------------------------------------
    // Phase-01 (home-and-my-quests Decision #42 + Codex Round 3 B3): AuthRepository
    // -----------------------------------------------------------------------

    @Test
    fun `appShellDataModule resolves AuthRepository given currentUidFlow`() {
        startKoin {
            modules(testDataSourceModule, testDaoModule, appShellDataModule())
        }

        val repo = getKoin().get<AuthRepository>()
        assertNotNull(repo)
        assertTrue(repo is AuthRepositoryImpl)
    }

    @Test
    fun `appShellDataModule binds AuthRepository as AuthRepositoryImpl with shared currentUidFlow`() = runTest {
        val uidSource = MutableStateFlow<String?>("user-A")
        startKoin {
            modules(testDataSourceModule, testDaoModule, appShellDataModule { uidSource.asStateFlow() })
        }

        val auth = getKoin().get<AuthRepository>()
        // currentUid() returns the real Firebase UID without LOCAL_UID substitution
        assertEquals("user-A", auth.currentUid())

        // observeUid() reflects the same source as UserStatsRepositoryImpl.currentUidFlow
        uidSource.value = null
        assertNull(auth.currentUid())
    }

    // -----------------------------------------------------------------------
    // Phase-07 H5: full-stack wiring — all 3 modules
    // GIVEN testDataSourceModule + appShellDataModule + appShellPresentationModule
    //   + questPresentationModule + quizzesPresentationModule + domain + repo stubs
    // WHEN get<DefaultRootComponent> with parametersOf(ComponentContext)
    // THEN component resolves without MissingDefinitionException
    // -----------------------------------------------------------------------

    @Test
    fun `full stack wiring DefaultRootComponent resolvable with parametersOf`() {
        val lifecycle = LifecycleRegistry()
        lifecycle.resume()
        val testCtx = DefaultComponentContext(lifecycle)

        startKoin {
            modules(
                testDataSourceModule,
                testDaoModule,
                testWorkManagerModule,
                testRepositoryStubsModule,
                appShellDataModule(),
                questDomainModule,
                catalogDomainModule,
                questPresentationModule,
                quizzesPresentationModule,
                appShellPresentationModule,
            )
        }

        try {
            val component = getKoin().get<DefaultRootComponent> { parametersOf(testCtx) }
            assertNotNull(component)
        } finally {
            lifecycle.stop()
            lifecycle.destroy()
        }
    }

    // -----------------------------------------------------------------------
    // Phase-07: system back event at LOCAL root
    // Spec: back_on_LOCAL_root_emits_system_back (Journey 4 step 3)
    // GIVEN DefaultRootComponent at LOCAL root (backStack empty, drawer closed)
    // WHEN onDestination(Back)
    // THEN events flow emits RootEvent.SystemBack
    // -----------------------------------------------------------------------

    @Test
    fun `system back event emitted at LOCAL root`() = runTest {
        val fakeStats = MutableStateFlow(UserStats.guest())
        val fakeRepo = object : UserStatsRepository {
            override fun observeStats(): Flow<UserStats> = fakeStats.asStateFlow()
            override suspend fun currentStats(): UserStats = fakeStats.value
            override suspend fun setLocalDeveloperLevel(value: Int) = Unit
            override suspend fun refreshProfile(): Result<Unit> = Result.success(Unit)
        }
        val lifecycle = LifecycleRegistry()
        lifecycle.resume()
        val testCtx = DefaultComponentContext(lifecycle)

        val component = DefaultRootComponent(
            componentContext = testCtx,
            initUseCase = InitializeAppShellUseCase(fakeRepo),
            navigateUseCase = NavigateUseCase(),
            observeUseCase = ObserveAppShellStateUseCase(fakeRepo),
            retapUseCase = OnTabRetapUseCase(),
            userStatsRepository = fakeRepo,
            workManager = mock(WorkManager::class.java),
            homeQuestsFactory = { _, _ ->
                object : HomeQuestsComponent {
                    override val state = MutableStateFlow(HomeQuestsUiState())
                    override fun onCatalogClick(id: CatalogId) = Unit
                }
            },
            myQuestsFactory = { _, _, _ ->
                object : MyQuestsComponent {
                    override val state = MutableStateFlow(MyQuestsUiState())
                    override fun onCatalogSelected(id: CatalogId?) = Unit
                    override fun onCreateQuestClick() = Unit
                    override fun onQuestClick(quest: QuestDisplayItem) = Unit
                }
            },
            quizzesFactory = { _ ->
                object : QuizzesComponent {
                    override val childStack: Value<ChildStack<QuizzesConfig, QuizzesChild>> =
                        MutableValue(
                            ChildStack(
                                active = Child.Created(QuizzesConfig.Idle, QuizzesChild.Idle),
                                backStack = emptyList(),
                            )
                        )
                    override fun openQuestList(catalogId: CatalogId, catalogName: String) = Unit
                    override fun openSectionList(questId: QuestId, titles: List<String>) = Unit
                    override fun dismissQuizzes() = Unit
                    override fun popToLevel(uiLevel: Int) = Unit
                }
            },
        )

        val events = mutableListOf<RootEvent>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            component.events.toList(events)
        }

        component.onDestination(Destination.Back)

        job.cancel()
        lifecycle.stop()
        lifecycle.destroy()
        assertTrue(events.any { it == RootEvent.SystemBack }, "Expected SystemBack in $events")
    }

    // -----------------------------------------------------------------------
    // Phase-07: deep link stub — no exception
    // Spec: deep_link_stub_no_crash (overview.md Tests Required)
    // GIVEN DefaultRootComponent (MVP stub — no URL patterns registered)
    // WHEN onDeepLink(DeepLink("schoolquiz://test"))
    // THEN no exception thrown
    // -----------------------------------------------------------------------

    @Test
    fun `deep link stub no crash`() {
        val fakeStats = MutableStateFlow(UserStats.guest())
        val fakeRepo = object : UserStatsRepository {
            override fun observeStats(): Flow<UserStats> = fakeStats.asStateFlow()
            override suspend fun currentStats(): UserStats = fakeStats.value
            override suspend fun setLocalDeveloperLevel(value: Int) = Unit
            override suspend fun refreshProfile(): Result<Unit> = Result.success(Unit)
        }
        val lifecycle = LifecycleRegistry()
        lifecycle.resume()
        val testCtx = DefaultComponentContext(lifecycle)

        val component = DefaultRootComponent(
            componentContext = testCtx,
            initUseCase = InitializeAppShellUseCase(fakeRepo),
            navigateUseCase = NavigateUseCase(),
            observeUseCase = ObserveAppShellStateUseCase(fakeRepo),
            retapUseCase = OnTabRetapUseCase(),
            userStatsRepository = fakeRepo,
            workManager = mock(WorkManager::class.java),
            homeQuestsFactory = { _, _ ->
                object : HomeQuestsComponent {
                    override val state = MutableStateFlow(HomeQuestsUiState())
                    override fun onCatalogClick(id: CatalogId) = Unit
                }
            },
            myQuestsFactory = { _, _, _ ->
                object : MyQuestsComponent {
                    override val state = MutableStateFlow(MyQuestsUiState())
                    override fun onCatalogSelected(id: CatalogId?) = Unit
                    override fun onCreateQuestClick() = Unit
                    override fun onQuestClick(quest: QuestDisplayItem) = Unit
                }
            },
            quizzesFactory = { _ ->
                object : QuizzesComponent {
                    override val childStack: Value<ChildStack<QuizzesConfig, QuizzesChild>> =
                        MutableValue(
                            ChildStack(
                                active = Child.Created(QuizzesConfig.Idle, QuizzesChild.Idle),
                                backStack = emptyList(),
                            )
                        )
                    override fun openQuestList(catalogId: CatalogId, catalogName: String) = Unit
                    override fun openSectionList(questId: QuestId, titles: List<String>) = Unit
                    override fun dismissQuizzes() = Unit
                    override fun popToLevel(uiLevel: Int) = Unit
                }
            },
        )

        component.onDeepLink(DeepLink("schoolquiz://test"))

        lifecycle.stop()
        lifecycle.destroy()
    }
}
