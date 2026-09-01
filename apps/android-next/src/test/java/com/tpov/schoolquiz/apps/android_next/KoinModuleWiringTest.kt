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
import com.tpov.schoolquiz.android.feature.quest.presentation.DraftQuestDisplayItem
import com.tpov.schoolquiz.android.feature.quest.presentation.MyQuestsComponent
import com.tpov.schoolquiz.android.feature.quest.presentation.MyQuestsUiState
import com.tpov.schoolquiz.android.feature.quest.presentation.di.questPresentationModule
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesChild
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesComponent
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.BreadcrumbRoot
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.di.quizzesPresentationModule
import com.tpov.schoolquiz.shared.core.catalog.domain.di.catalogDomainModule
import com.tpov.schoolquiz.shared.core.catalog.domain.model.Catalog
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.core.catalog.domain.repository.CatalogRepository
import com.tpov.schoolquiz.shared.core.persistence.UserStatsDao
import com.tpov.schoolquiz.shared.core.persistence.UserStatsEntity
import com.tpov.schoolquiz.shared.core.sync.SyncFrequency
import com.tpov.schoolquiz.shared.core.sync.SyncScheduler
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
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerComponentFactory
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.Lesson
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.SessionMode
import com.tpov.schoolquiz.shared.feature.lesson.domain.repository.LessonRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonAttemptRepository
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerRootComponent
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component.DefaultLessonRunnerRootComponent
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.di.lessonRunnerPresentationModule
import com.tpov.schoolquiz.shared.core.analytics.AnalyticsTracker
import com.tpov.schoolquiz.shared.core.analytics.NoOpAnalyticsTracker
import com.tpov.schoolquiz.shared.core.persistence.LessonAttemptDao
import com.tpov.schoolquiz.shared.core.persistence.QuestionRepetitionDao
import com.tpov.schoolquiz.shared.core.persistence.LessonDao
import com.tpov.schoolquiz.shared.core.persistence.LessonRatingLocalDao
import com.tpov.schoolquiz.shared.core.persistence.QuestDao
import com.tpov.schoolquiz.shared.core.persistence.SectionDao
import com.tpov.schoolquiz.shared.core.persistence.StringSetConverter
import com.tpov.schoolquiz.shared.core.persistence.ThemeDao
import com.tpov.schoolquiz.shared.core.persistence.TopParticipantListConverter
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.core.question_schema.KotlinxSerializationQuestionContentParser
import com.tpov.schoolquiz.shared.core.question_schema.QuestionContentParser
import com.tpov.schoolquiz.shared.core.question_schema.di.questionSchemaModule
import com.tpov.schoolquiz.shared.feature.question.domain.di.questionDomainModule
import com.tpov.schoolquiz.shared.feature.economy.domain.di.economyDomainModule
import com.tpov.schoolquiz.shared.feature.economy.domain.model.GiftBoxOpening
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.GiftBoxRepository
import com.tpov.schoolquiz.shared.feature.internet.leaderboard.data.di.leaderboardDataModule
import com.tpov.schoolquiz.shared.feature.internet.leaderboard.data.remote.TournamentLeaderboardRemoteDataSource
import com.tpov.schoolquiz.shared.feature.internet.leaderboard.data.remote.TournamentOverviewDto
import com.tpov.schoolquiz.shared.feature.internet.leaderboard.data.remote.TournamentSummaryDto
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.di.profileDomainModule
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.UserProfile
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.repository.ProfileRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.di.lessonRunnerDataModule
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonCommentRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.di.lessonRunnerDomainKoinAdapter
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider.AttemptIdProvider
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider.RandomSeedProvider
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider.RatingIdProvider
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonRatingRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.AbortAttemptUseCase
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.CompleteAttemptUseCase
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.SubmitLessonRatingUseCase
import com.tpov.schoolquiz.shared.feature.question.domain.model.Question
import com.tpov.schoolquiz.shared.feature.question.domain.model.QuestionId
import com.tpov.schoolquiz.shared.feature.question.domain.repository.QuestionRepository
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.di.questAuthoringDomainModule
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftQuestion
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestAuthoringBundle
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraftId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraftStatus
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraftSummary
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.QuestAuthoringRepository
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
import com.tpov.schoolquiz.shared.feature.economy.domain.model.EconomyResourceBalance
import com.tpov.schoolquiz.shared.feature.economy.domain.model.LessonUnlockKind
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ReferralProgram
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ShopItemId
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ShopPurchaseResult
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.EconomyRepository

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

    private val testSyncSchedulerModule = module {
        single<SyncScheduler> {
            object : SyncScheduler {
                override fun enqueueManualSync() = Unit

                override fun enqueueManualProfileSync() = Unit

                override fun applyFrequency(frequency: SyncFrequency) = Unit
                override fun applyProfileFrequency(frequency: SyncFrequency) = Unit
            }
        }
    }

    private val testTournamentLeaderboardRemoteModule = module {
        single<TournamentLeaderboardRemoteDataSource> {
            object : TournamentLeaderboardRemoteDataSource {
                override suspend fun fetchOverview(
                    tournamentId: String,
                    limit: Int,
                ): TournamentOverviewDto =
                    TournamentOverviewDto(
                        tournament =
                            TournamentSummaryDto(
                                id = tournamentId,
                                sourceShelf = tournamentId,
                                title = "Test tournament",
                                stageLabel = "Test stage",
                                updatedAtMs = 0L,
                                leaderboardUpdatedAtMs = 0L,
                            ),
                        metadata = null,
                        leaderboard = emptyList(),
                        participants = emptyList(),
                        currentUserEntry = null,
                        currentUserParticipant = null,
                    )
            }
        }
    }

    private val testRepositoryStubsModule = module {
        single<EconomyRepository> {
            object : EconomyRepository {
                override fun observeBalance(): Flow<EconomyResourceBalance> =
                    flowOf(EconomyResourceBalance())

                override suspend fun currentBalance(): EconomyResourceBalance = EconomyResourceBalance()

                override suspend fun purchase(itemId: ShopItemId): Result<ShopPurchaseResult> =
                    Result.failure(UnsupportedOperationException("not expected"))

                override suspend fun unlockLesson(
                    lessonId: String,
                    kind: LessonUnlockKind,
                ): Result<EconomyResourceBalance> =
                    Result.failure(UnsupportedOperationException("not expected"))

                override suspend fun referralProgram(): ReferralProgram = ReferralProgram("", emptyList())
            }
        }
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
        single<QuestAuthoringRepository> {
            object : QuestAuthoringRepository {
                override fun observeDraftSummaries(ownerUid: String): Flow<List<QuestDraftSummary>> =
                    flowOf(emptyList())

                override fun observeDraft(draftId: QuestDraftId): Flow<QuestAuthoringBundle?> =
                    flowOf(null)

                override suspend fun getDraft(draftId: QuestDraftId): QuestAuthoringBundle? = null

                override suspend fun getActiveDraft(ownerUid: String): QuestAuthoringBundle? = null

                override suspend fun saveDraft(bundle: QuestAuthoringBundle): Result<Unit> = Result.success(Unit)

                override suspend fun upsertQuestion(question: DraftQuestion): Result<Unit> = Result.success(Unit)

                override suspend fun setDraftStatus(
                    draftId: QuestDraftId,
                    status: QuestDraftStatus,
                    updatedAtMs: Long,
                ): Result<Unit> = Result.success(Unit)
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
            }
        }
        single<QuestionRepository> {
            object : QuestionRepository {
                override fun observeByLesson(lessonId: LessonId) = flowOf(emptyList<Question>())
                override suspend fun getById(id: QuestionId): Question? = null
                override suspend fun refreshByParents(
                    lessonIds: Set<LessonId>,
                    cursor: Long,
                ): Result<Unit> = Result.success(Unit)
            }
        }
        single<LessonAttemptRepository> {
            object : LessonAttemptRepository {
                override suspend fun save(attempt: Attempt): Result<Unit> = Result.success(Unit)
                override fun observeByLesson(userId: String, lessonId: LessonId): Flow<List<Attempt>> = flowOf(emptyList())
                override fun observeAllByUser(userId: String): Flow<List<Attempt>> = flowOf(emptyList())
            }
        }
        single<ProfileRepository> {
            object : ProfileRepository {
                override fun observeCurrentProfile(): Flow<UserProfile> = flowOf(UserProfile.offline())
                override suspend fun currentProfile(): UserProfile = UserProfile.offline()
                override suspend fun ensureCurrentProfile(): Result<UserProfile> = Result.success(UserProfile.offline())
                override suspend fun updateNickname(nickname: String): Result<UserProfile> =
                    Result.success(UserProfile.offline().copy(nickname = nickname))
            }
        }
        single<GiftBoxRepository> {
            object : GiftBoxRepository {
                override suspend fun openGiftBox(): Result<GiftBoxOpening> =
                    Result.failure(IllegalStateException("No gift boxes available"))
            }
        }
        single<LessonRunnerComponentFactory> {
            LessonRunnerComponentFactory { _, _, _, _ -> error("Not wired in KoinModuleWiringTest") }
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
                testSyncSchedulerModule,
                testTournamentLeaderboardRemoteModule,
                testRepositoryStubsModule,
                appShellDataModule(),
                leaderboardDataModule,
                questDomainModule,
                questAuthoringDomainModule,
                catalogDomainModule,
                profileDomainModule,
                economyDomainModule,
                // Список уроков считает цену открытия из вопросов урока — ему нужны и репозиторий
                // вопросов, и разборщик payload.
                questionDomainModule,
                questionSchemaModule,
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
            syncScheduler = object : SyncScheduler {
                override fun enqueueManualSync() = Unit

                override fun enqueueManualProfileSync() = Unit

                override fun applyFrequency(frequency: SyncFrequency) = Unit
                override fun applyProfileFrequency(frequency: SyncFrequency) = Unit
            },
            homeQuestsFactory = { _, _, _ ->
                object : HomeQuestsComponent {
                    override val state = MutableStateFlow(HomeQuestsUiState())
                    override fun onCatalogClick(id: CatalogId, name: String) = Unit
                    override fun onContinueClick() = Unit
                }
            },
            myQuestsFactory = { _, _, _ ->
                object : MyQuestsComponent {
                    override val state = MutableStateFlow(MyQuestsUiState())
                    override fun onCatalogSelected(id: CatalogId?) = Unit
                    override fun onCreateQuestClick() = Unit
                    override fun onQuestClick(quest: QuestDisplayItem) = Unit
                    override fun onDraftClick(draft: DraftQuestDisplayItem) = Unit
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
                    override fun openLessonRunner(lessonId: String) = Unit
                    override val currentCatalogName: StateFlow<String?> = MutableStateFlow(null)
                    override val currentCatalogIcons: StateFlow<List<androidx.compose.ui.graphics.vector.ImageVector>> =
                        MutableStateFlow(emptyList<androidx.compose.ui.graphics.vector.ImageVector>())
                    override fun openQuestList(catalogId: CatalogId, catalogName: String) = Unit
                    override fun openCourseArena() = Unit
                    override fun openCourseArchive() = Unit
                    override fun openPublicQuestCatalogPicker(targetShelf: String) = Unit
                    override fun openPublicQuestShelfCatalog(
                        targetShelf: String,
                        forcedHardMode: Boolean?,
                    ) = Unit
                    override fun openSectionList(questId: QuestId, breadcrumbs: List<BreadcrumbRoot>) = Unit
                    override fun dismissQuizzes() = Unit
                    override fun popToLevel(uiLevel: Int) = Unit
                    override fun popCurrentChild() = Unit
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
            syncScheduler = object : SyncScheduler {
                override fun enqueueManualSync() = Unit

                override fun enqueueManualProfileSync() = Unit

                override fun applyFrequency(frequency: SyncFrequency) = Unit
                override fun applyProfileFrequency(frequency: SyncFrequency) = Unit
            },
            homeQuestsFactory = { _, _, _ ->
                object : HomeQuestsComponent {
                    override val state = MutableStateFlow(HomeQuestsUiState())
                    override fun onCatalogClick(id: CatalogId, name: String) = Unit
                    override fun onContinueClick() = Unit
                }
            },
            myQuestsFactory = { _, _, _ ->
                object : MyQuestsComponent {
                    override val state = MutableStateFlow(MyQuestsUiState())
                    override fun onCatalogSelected(id: CatalogId?) = Unit
                    override fun onCreateQuestClick() = Unit
                    override fun onQuestClick(quest: QuestDisplayItem) = Unit
                    override fun onDraftClick(draft: DraftQuestDisplayItem) = Unit
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
                    override fun openLessonRunner(lessonId: String) = Unit
                    override val currentCatalogName: StateFlow<String?> = MutableStateFlow(null)
                    override val currentCatalogIcons: StateFlow<List<androidx.compose.ui.graphics.vector.ImageVector>> =
                        MutableStateFlow(emptyList<androidx.compose.ui.graphics.vector.ImageVector>())
                    override fun openQuestList(catalogId: CatalogId, catalogName: String) = Unit
                    override fun openCourseArena() = Unit
                    override fun openCourseArchive() = Unit
                    override fun openPublicQuestCatalogPicker(targetShelf: String) = Unit
                    override fun openPublicQuestShelfCatalog(
                        targetShelf: String,
                        forcedHardMode: Boolean?,
                    ) = Unit
                    override fun openSectionList(questId: QuestId, breadcrumbs: List<BreadcrumbRoot>) = Unit
                    override fun dismissQuizzes() = Unit
                    override fun popToLevel(uiLevel: Int) = Unit
                    override fun popCurrentChild() = Unit
                }
            },
        )

        component.onDeepLink(DeepLink("schoolquiz://test"))

        lifecycle.stop()
        lifecycle.destroy()
    }

    // ── Phase-07: IT-09 — Koin module wiring tests ───────────────────────────

    private val testLessonAttemptDaoStub = module {
        single<LessonAttemptDao> { mock(LessonAttemptDao::class.java) }
        // The attempt repository now advances the repetition schedule alongside the attempt.
        single<QuestionRepetitionDao> { mock(QuestionRepetitionDao::class.java) }
    }

    private val testLessonRatingLocalDaoStub = module {
        single<LessonRatingLocalDao> { mock(LessonRatingLocalDao::class.java) }
    }

    private val testLessonResultOutboxDepsStub = module {
        single<LessonDao> { mock(LessonDao::class.java) }
        single<ThemeDao> { mock(ThemeDao::class.java) }
        single<SectionDao> { mock(SectionDao::class.java) }
        single<QuestDao> { mock(QuestDao::class.java) }
    }

    private val testRunnerRepositoryStubs = module {
        single<QuestionRepository> { mock(QuestionRepository::class.java) }
        single<LessonRepository> { mock(LessonRepository::class.java) }
        single<AuthRepository> { mock(AuthRepository::class.java) }
        // The discussion sits on the result screen, so building the runner needs this binding even
        // in tests that never open a comment. The app supplies it from firebaseLessonCommentModule,
        // which is a platform module and cannot be started here.
        single<LessonCommentRepository> { mock(LessonCommentRepository::class.java) }
    }

    /** The runner reads lives from the profile; tests that never touch it still need the binding. */
    /**
     * Analytics for the wiring tests.
     *
     * The real binding needs an Android Context for Firebase, which a JVM unit test does not
     * have. Binding the no-op here keeps the production module honestly *requiring* a tracker —
     * if the app ever stops providing one, this test still fails, which is its whole job.
     */
    private val testAnalyticsStub = module {
        single<AnalyticsTracker> { NoOpAnalyticsTracker }
    }

    private val testProfileRepositoryStub = module {
        single<ProfileRepository> {
            object : ProfileRepository {
                override fun observeCurrentProfile(): Flow<UserProfile> = flowOf(UserProfile.offline())
                override suspend fun currentProfile(): UserProfile = UserProfile.offline()
                override suspend fun ensureCurrentProfile(): Result<UserProfile> =
                    Result.success(UserProfile.offline())
                override suspend fun updateNickname(nickname: String): Result<UserProfile> =
                    Result.success(UserProfile.offline().copy(nickname = nickname))
            }
        }
    }

    // ── IT-09a ────────────────────────────────────────────────────────────────

    /**
     * IT-09a: GIVEN lessonRunnerDataModule WHEN get<LessonAttemptRepository>()
     * THEN resolves without NoBeanDefinitionFoundException.
     * Spec: docs/features/lesson-runner/plan/phase-07/tests.md §IT-09a
     */
    @Test
    fun `it09a lessonAttemptRepository resolves from lessonRunnerDataModule`() {
        startKoin {
            modules(testLessonAttemptDaoStub, testLessonRatingLocalDaoStub, testLessonResultOutboxDepsStub, lessonRunnerDataModule)
        }
        assertNotNull(getKoin().get<LessonAttemptRepository>())
    }

    // ── IT-09b ────────────────────────────────────────────────────────────────

    /**
     * IT-09b: GIVEN lessonRunnerDataModule WHEN get<LessonRatingRepository>()
     * THEN resolves without exception.
     * Spec: docs/features/lesson-runner/plan/phase-07/tests.md §IT-09b
     */
    @Test
    fun `it09b lessonRatingRepository resolves from lessonRunnerDataModule`() {
        startKoin {
            modules(testLessonAttemptDaoStub, testLessonRatingLocalDaoStub, testLessonResultOutboxDepsStub, lessonRunnerDataModule)
        }
        assertNotNull(getKoin().get<LessonRatingRepository>())
    }

    // ── IT-09c ────────────────────────────────────────────────────────────────

    /**
     * IT-09c: GIVEN lessonRunnerDataModule WHEN get<AttemptIdProvider> / RandomSeedProvider / RatingIdProvider
     * THEN each resolves to correct default impl.
     * Spec: docs/features/lesson-runner/plan/phase-07/tests.md §IT-09c
     */
    @Test
    fun `it09c providers AttemptIdProvider RandomSeedProvider RatingIdProvider resolve`() {
        startKoin { modules(lessonRunnerDataModule) }
        assertNotNull(getKoin().get<AttemptIdProvider>())
        assertNotNull(getKoin().get<RandomSeedProvider>())
        assertNotNull(getKoin().get<RatingIdProvider>())
    }

    // ── IT-09d ────────────────────────────────────────────────────────────────

    /**
     * IT-09d: GIVEN lessonRunnerDomainKoinAdapter WHEN get<CompleteAttemptUseCase> / AbortAttemptUseCase / SubmitLessonRatingUseCase
     * THEN each resolves without exception.
     * Spec: docs/features/lesson-runner/plan/phase-07/tests.md §IT-09d
     */
    @Test
    fun `it09d useCases CompleteAttemptUseCase AbortAttemptUseCase SubmitLessonRatingUseCase resolve`() {
        startKoin {
            modules(
                testLessonAttemptDaoStub,
                testLessonRatingLocalDaoStub,
                testLessonResultOutboxDepsStub,
                testRunnerRepositoryStubs,
                testProfileRepositoryStub,
                lessonRunnerDataModule,
                lessonRunnerDomainKoinAdapter,
                questionSchemaModule,
            )
        }
        assertNotNull(getKoin().get<CompleteAttemptUseCase>())
        assertNotNull(getKoin().get<AbortAttemptUseCase>())
        assertNotNull(getKoin().get<SubmitLessonRatingUseCase>())
    }

    // ── IT-09e ────────────────────────────────────────────────────────────────

    /**
     * IT-09e: GIVEN lessonRunnerPresentationModule + deps WHEN get<LessonRunnerRootComponent>(parametersOf(ctx, LessonId("l1"), EASY))
     * THEN resolves to DefaultLessonRunnerRootComponent without exception.
     * Spec: docs/features/lesson-runner/plan/phase-07/tests.md §IT-09e
     */
    @Test
    fun `it09e lessonRunnerRootComponent resolves with parametersOf ctx lessonId mode`() {
        val lifecycle = LifecycleRegistry()
        lifecycle.resume()
        val ctx = DefaultComponentContext(lifecycle)

        startKoin {
            modules(
                testLessonAttemptDaoStub,
                testLessonRatingLocalDaoStub,
                testLessonResultOutboxDepsStub,
                testRunnerRepositoryStubs,
                testProfileRepositoryStub,
                lessonRunnerDataModule,
                lessonRunnerDomainKoinAdapter,
                lessonRunnerPresentationModule,
                testAnalyticsStub,
                questionSchemaModule,
            )
        }

        try {
            val component = getKoin().get<LessonRunnerRootComponent> {
                parametersOf(ctx, LessonId("l1"), Difficulty.EASY, SessionMode.LEARNING)
            }
            assertNotNull(component)
            assertTrue(component is DefaultLessonRunnerRootComponent)
        } finally {
            lifecycle.stop()
            lifecycle.destroy()
        }
    }

    // ── IT-09f ────────────────────────────────────────────────────────────────

    /**
     * IT-09f: GIVEN questionSchemaModule WHEN get<QuestionContentParser>()
     * THEN resolves to KotlinxSerializationQuestionContentParser.
     * Spec: docs/features/lesson-runner/plan/phase-07/tests.md §IT-09f
     */
    @Test
    fun `it09f questionContentParser resolves to KotlinxSerializationQuestionContentParser`() {
        startKoin { modules(questionSchemaModule) }
        val parser = getKoin().get<QuestionContentParser>()
        assertNotNull(parser)
        assertTrue(parser is KotlinxSerializationQuestionContentParser)
    }

    // ── IT-09g ────────────────────────────────────────────────────────────────

    /**
     * IT-09g: TypeConverter classes instantiate without exception (JVM structural check).
     * persistenceModule uses androidContext() — AppDatabase cannot be built in JVM tests.
     * DifficultyConverter removed per ADR-LR-18; mapper handles Difficulty↔Int.
     * TopParticipantListConverter and StringSetConverter registered in persistenceModule.
     * Spec: docs/features/lesson-runner/plan/phase-07/tests.md §IT-09g
     */
    @Test
    fun `it09g appDatabase typeConverters TopParticipantListConverter StringSetConverter are instantiatable`() {
        val topParticipantListConverter = TopParticipantListConverter()
        val stringSetConverter = StringSetConverter()
        assertNotNull(topParticipantListConverter)
        assertNotNull(stringSetConverter)
    }

    // ── IT-09h ────────────────────────────────────────────────────────────────

    /**
     * IT-09h: GIVEN lessonRunnerPresentationModule WHEN get<LessonRunnerComponentFactory>()
     * THEN resolves without NoBeanDefinitionFoundException (single binding from presentation module).
     * Spec: docs/features/lesson-runner/plan/phase-07/tests.md §IT-09h
     */
    @Test
    fun `it09h lessonRunnerComponentFactory resolves as single binding`() {
        startKoin { modules(lessonRunnerPresentationModule) }
        val factory = getKoin().get<LessonRunnerComponentFactory>()
        assertNotNull(factory)
    }
}
