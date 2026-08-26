@file:Suppress("MaxLineLength")

package com.tpov.schoolquiz.android.feature.app_shell.presentation.component

import com.arkivanov.decompose.Child
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.backhandler.BackCallback
import com.arkivanov.essenty.backhandler.BackDispatcher
import com.arkivanov.essenty.backhandler.BackHandler
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.lifecycle.stop
import com.tpov.schoolquiz.android.core.designsystem.model.QuestDisplayItem
import com.tpov.schoolquiz.android.feature.app_shell.presentation.fake.FakeUserStatsRepository
import com.tpov.schoolquiz.android.feature.app_shell.presentation.fake.FakeSyncScheduler
import com.tpov.schoolquiz.android.feature.app_shell.presentation.fake.StubHomeQuestsComponent
import com.tpov.schoolquiz.android.feature.app_shell.presentation.fake.StubMyQuestsComponent
import com.tpov.schoolquiz.android.feature.quest.presentation.DefaultHomeQuestsComponent
import com.tpov.schoolquiz.android.feature.quest.presentation.DefaultMyQuestsComponent
import com.tpov.schoolquiz.android.feature.quest.presentation.fake.FakeAuthRepository
import com.tpov.schoolquiz.android.feature.quest.presentation.fake.FakeCatalogRepository
import com.tpov.schoolquiz.android.feature.quest.presentation.fake.FakeQuestAuthoringRepository
import com.tpov.schoolquiz.android.feature.quest.presentation.fake.FakeQuestRepository
import com.tpov.schoolquiz.android.feature.quest.presentation.fake.buildCatalog
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.DefaultQuizzesComponent
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuestListComponent
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesChild
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesComponent
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.QuestListUiState
import com.tpov.schoolquiz.shared.core.catalog.domain.model.Catalog
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.core.catalog.domain.repository.CatalogRepository
import com.tpov.schoolquiz.shared.core.catalog.domain.use_case.ObserveCatalogsUseCase
import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.InitializeAppShellUseCase
import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.NavigateUseCase
import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.ObserveAppShellStateUseCase
import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.OnTabRetapUseCase
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.LessonRunnerComponentFactory
import com.tpov.schoolquiz.shared.feature.economy.domain.model.GiftBoxOpening
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.GiftBoxRepository
import com.tpov.schoolquiz.shared.feature.economy.domain.use_case.OpenGiftBoxUseCase
import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.AuthRepository
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.UserProfile
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.repository.ProfileRepository
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.use_case.ObserveCurrentProfileUseCase
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.Lesson
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson.domain.repository.LessonRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonAttemptRepository
import com.tpov.schoolquiz.shared.feature.question.domain.model.Question
import com.tpov.schoolquiz.shared.feature.question.domain.model.QuestionId
import com.tpov.schoolquiz.shared.feature.question.domain.repository.QuestionRepository
import com.tpov.schoolquiz.shared.feature.quest.domain.model.Quest
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import com.tpov.schoolquiz.shared.feature.quest.domain.repository.QuestRepository
import com.tpov.schoolquiz.shared.feature.quest.domain.use_case.ObserveMyQuestsUseCase
import com.tpov.schoolquiz.shared.feature.quest.domain.use_case.SetPublicQuestShelfUseCase
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case.ObserveQuestDraftSummariesUseCase
import com.tpov.schoolquiz.shared.feature.section.domain.model.Section
import com.tpov.schoolquiz.shared.feature.section.domain.model.SectionId
import com.tpov.schoolquiz.shared.feature.section.domain.repository.SectionRepository
import com.tpov.schoolquiz.shared.feature.theme.domain.model.Theme
import com.tpov.schoolquiz.shared.feature.theme.domain.model.ThemeId
import com.tpov.schoolquiz.shared.feature.theme.domain.repository.ThemeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * JVM integration tests for [DefaultRootComponent] cross-feature wiring.
 *
 * Verifies that lambda closures in DefaultRootComponent correctly connect:
 * - HomeQuestsComponent.onCatalogClick → QuizzesComponent.openQuestList (WIRE-01)
 * - MyQuestsComponent.onQuestClick → QuizzesComponent.openSectionList (WIRE-02)
 * - catalogName resolved from homeQuestsComponent.state.value.catalogs (WIRE-03)
 * - dismissQuizzes → overlay hidden (WIRE-04)
 * - Root backCallback lower priority than QuizzesComponent backCallback (INT-04)
 * - QuizzesComponent backCallback disabled when Idle → Root handles back (INT-05)
 *
 * Spec: docs/features/quizzes-screen/04-testing.md §10
 * Traceability: AC#1 (HomeQuests→openQuestList), AC#7 (MyQuests→openSectionList),
 *               AC#6 (dismiss), INT-04/INT-05 (BackCallback priority)
 *
 * NOTE: This file requires `app-shell:presentation` to depend on `quizzes-screen:presentation`.
 * OQ-1: request backend-dev to add
 * `implementation(project(":android:feature:quizzes-screen:presentation"))` to
 * android/feature/app-shell/presentation/build.gradle.kts.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class QuizzesRootIntegrationTest {

    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = UnconfinedTestDispatcher(testScheduler)
    private lateinit var lifecycle: LifecycleRegistry

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        if (::lifecycle.isInitialized) {
            lifecycle.stop()
            lifecycle.destroy()
        }
        Dispatchers.resetMain()
    }

    private fun testCtx(backHandler: BackHandler? = null): DefaultComponentContext {
        lifecycle = LifecycleRegistry()
        lifecycle.resume()
        return DefaultComponentContext(lifecycle = lifecycle, backHandler = backHandler)
    }

    /** Records BackCallback registrations for priority inspection (INT-04). */
    private class TestBackHandler : BackHandler {
        val registeredCallbacks = mutableListOf<BackCallback>()
        override fun register(callback: BackCallback) { registeredCallbacks += callback }
        override fun unregister(callback: BackCallback) { registeredCallbacks -= callback }
        override fun isRegistered(callback: BackCallback) = registeredCallbacks.contains(callback)
    }

    /** Minimal QuestListComponent stub for constructing QuizzesChild.QuestList in test stacks. */
    private class StubQuestListComponent : QuestListComponent {
        override val uiState: Value<QuestListUiState> = MutableValue(QuestListUiState.Loading)
        override val titles: List<String> = emptyList()
        override fun onQuestClick(quest: QuestDisplayItem) = Unit
        override fun onQuestDownloadClick(quest: QuestDisplayItem) = Unit
        override fun onShareClick(quest: QuestDisplayItem) = Unit
    }

    /** Captured QuizzesComponent — records calls without real navigation. */
    private class CapturedQuizzesComponent : QuizzesComponent {

        data class OpenQuestListArgs(val catalogId: CatalogId, val catalogName: String)
        data class OpenSectionListArgs(val questId: QuestId, val titles: List<String>)

        var openQuestListArgs: OpenQuestListArgs? = null
        var openSectionListArgs: OpenSectionListArgs? = null
        var dismissCalled = false

        private val _childStack: MutableValue<ChildStack<QuizzesConfig, QuizzesChild>> = MutableValue(idleStack())
        override val childStack: Value<ChildStack<QuizzesConfig, QuizzesChild>> get() = _childStack
        override val currentCatalogName: kotlinx.coroutines.flow.StateFlow<String?> =
            kotlinx.coroutines.flow.MutableStateFlow(null)
        override val currentCatalogIcons: kotlinx.coroutines.flow.StateFlow<List<androidx.compose.ui.graphics.vector.ImageVector>> =
            kotlinx.coroutines.flow.MutableStateFlow(emptyList<androidx.compose.ui.graphics.vector.ImageVector>())

        override fun openQuestList(catalogId: CatalogId, catalogName: String) {
            openQuestListArgs = OpenQuestListArgs(catalogId, catalogName)
            _childStack.value = questListStack(catalogId, catalogName)
        }

        override fun openCourseArchive() = Unit
        override fun openCourseArena() = Unit
        override fun openPublicQuestCatalogPicker(targetShelf: String, title: String) = Unit
        override fun openPublicQuestShelfCatalog(
            targetShelf: String,
            title: String,
            forcedHardMode: Boolean?,
        ) = Unit

        override fun openSectionList(questId: QuestId, titles: List<String>) {
            openSectionListArgs = OpenSectionListArgs(questId, titles)
        }

        override fun dismissQuizzes() {
            dismissCalled = true
            _childStack.value = idleStack()
        }

        override fun popToLevel(uiLevel: Int) = Unit
        override fun popCurrentChild() = Unit
        override fun openLessonRunner(lessonId: String) = Unit

        private fun idleStack() = ChildStack(
            active = Child.Created(configuration = QuizzesConfig.Idle, instance = QuizzesChild.Idle),
            backStack = emptyList(),
        )

        private fun questListStack(catalogId: CatalogId, catalogName: String): ChildStack<QuizzesConfig, QuizzesChild> {
            val config = QuizzesConfig.QuestList(catalogId.value, listOf(catalogName))
            return ChildStack(
                active = Child.Created(configuration = config, instance = QuizzesChild.QuestList(StubQuestListComponent())),
                backStack = emptyList(),
            )
        }
    }

    private fun buildWiringComponent(
        fakeRepo: FakeUserStatsRepository = FakeUserStatsRepository(),
        catalogRepo: FakeCatalogRepository = FakeCatalogRepository(),
        questRepo: FakeQuestRepository = FakeQuestRepository(),
        capturedQuizzes: CapturedQuizzesComponent = CapturedQuizzesComponent(),
        backHandler: BackHandler? = null,
    ) = DefaultRootComponent(
        componentContext = testCtx(backHandler),
        initUseCase = InitializeAppShellUseCase(fakeRepo),
        navigateUseCase = NavigateUseCase(),
        observeUseCase = ObserveAppShellStateUseCase(fakeRepo),
        retapUseCase = OnTabRetapUseCase(),
        userStatsRepository = fakeRepo,
        syncScheduler = FakeSyncScheduler(),
        homeQuestsFactory = { ctx, onCatalogDrillDown ->
            DefaultHomeQuestsComponent(
                componentContext = ctx,
                observeCatalogs = ObserveCatalogsUseCase(catalogRepo),
                observeProfile = ObserveCurrentProfileUseCase(OfflineProfileRepository()),
                openGiftBoxUseCase = OpenGiftBoxUseCase(NoGiftBoxRepository),
                onCatalogDrillDown = onCatalogDrillDown,
                mainContext = testDispatcher,
            )
        },
        myQuestsFactory = { ctx, navigator, onQuestDrillDown ->
            DefaultMyQuestsComponent(
                componentContext = ctx,
                authRepo = FakeAuthRepository(initialUid = "user1"),
                observeMyQuests = ObserveMyQuestsUseCase(questRepo),
                observeDraftSummaries = ObserveQuestDraftSummariesUseCase(FakeQuestAuthoringRepository()),
                observeCatalogs = ObserveCatalogsUseCase(catalogRepo),
                navigator = navigator,
                onQuestDrillDown = onQuestDrillDown,
                mainContext = testDispatcher,
            )
        },
        quizzesFactory = { _ -> capturedQuizzes },
    )

    /**
     * Builds DefaultRootComponent with a real [DefaultQuizzesComponent] so that
     * BackCallback registration from both root and quizzes can be inspected (INT-04/INT-05).
     */
    private fun buildWithRealQuizzes(
        backHandler: BackHandler? = null,
    ) = DefaultRootComponent(
        componentContext = testCtx(backHandler),
        initUseCase = InitializeAppShellUseCase(FakeUserStatsRepository()),
        navigateUseCase = NavigateUseCase(),
        observeUseCase = ObserveAppShellStateUseCase(FakeUserStatsRepository()),
        retapUseCase = OnTabRetapUseCase(),
        userStatsRepository = FakeUserStatsRepository(),
        syncScheduler = FakeSyncScheduler(),
        homeQuestsFactory = { _, _ -> StubHomeQuestsComponent },
        myQuestsFactory = { _, _, _ -> StubMyQuestsComponent },
        quizzesFactory = { ctx ->
            val questRepository =
                object : QuestRepository {
                    override fun observeMyQuests(authorUid: String, catalogId: CatalogId?): Flow<List<Quest>> = flowOf(emptyList())
                    override fun observeByCatalog(catalogId: CatalogId, shelf: String): Flow<List<Quest>> = flowOf(emptyList())
                    override fun observeByShelf(shelf: String): Flow<List<Quest>> = flowOf(emptyList())
                    override suspend fun getById(id: QuestId): Quest? = null
                    override suspend fun refreshFromRemote(
                        currentUserUid: String?,
                        availableShelves: Set<String>,
                        catalogIdsToSync: Set<CatalogId>,
                        cursor: Long,
                    ): Result<Set<QuestId>> = Result.success(emptySet())
                }
            DefaultQuizzesComponent(
                componentContext = ctx,
                questRepository = questRepository,
                sectionRepository = object : SectionRepository {
                    override fun observeByQuest(questId: QuestId): Flow<List<Section>> = flowOf(emptyList())
                    override suspend fun getById(id: SectionId): Section? = null
                    override suspend fun refreshByParents(questIds: Set<QuestId>, cursor: Long): Result<Set<SectionId>> = Result.success(emptySet())
                    override suspend fun getLocalContentsVersion(id: SectionId): Long? = null
                },
                themeRepository = object : ThemeRepository {
                    override fun observeBySection(sectionId: SectionId): Flow<List<Theme>> = flowOf(emptyList())
                    override suspend fun getById(id: ThemeId): Theme? = null
                    override suspend fun refreshByParents(sectionIds: Set<SectionId>, cursor: Long): Result<Set<ThemeId>> = Result.success(emptySet())
                    override suspend fun getLocalContentsVersion(id: ThemeId): Long? = null
                },
                lessonRepository = object : LessonRepository {
                    override fun observeByTheme(themeId: ThemeId): Flow<List<Lesson>> = flowOf(emptyList())
                    override suspend fun getById(id: LessonId): Lesson? = null
                    override suspend fun refreshByParents(themeIds: Set<ThemeId>, cursor: Long): Result<Set<LessonId>> = Result.success(emptySet())
                    override suspend fun getLocalContentsVersion(id: LessonId): Long? = null
                },
                lessonAttemptRepository = object : LessonAttemptRepository {
                    override suspend fun save(attempt: Attempt): Result<Unit> = Result.success(Unit)
                    override fun observeByLesson(userId: String, lessonId: LessonId): Flow<List<Attempt>> = flowOf(emptyList())
                    override fun observeAllByUser(userId: String): Flow<List<Attempt>> = flowOf(emptyList())
                },
                authRepository = object : AuthRepository {
                    override suspend fun currentUid(): String? = null
                    override fun observeUid(): Flow<String?> = flowOf(null)
                },
                questionRepository = object : QuestionRepository {
                    override fun observeByLesson(lessonId: LessonId): Flow<List<Question>> = flowOf(emptyList())
                    override suspend fun getById(id: QuestionId): Question? = null
                    override suspend fun refreshByParents(lessonIds: Set<LessonId>, cursor: Long): Result<Unit> = Result.success(Unit)
                },
                catalogRepository = object : CatalogRepository {
                    override fun observeAll(): Flow<List<Catalog>> = flowOf(emptyList())
                    override suspend fun refreshFromRemote(): Result<Set<CatalogId>> = Result.success(emptySet())
                    override suspend fun getById(id: CatalogId): Catalog? = null
                },
                setPublicQuestShelf = SetPublicQuestShelfUseCase(questRepository),
                lessonRunnerFactory = LessonRunnerComponentFactory { _, _, _, _ -> error("Not expected") },
                mainContext = testDispatcher,
            )
        },
    )

    // ── WIRE-01 — HomeQuestsComponent.onCatalogClick triggers QuizzesComponent.openQuestList ──
    // Spec: 04-testing.md §10 INT-01
    // GIVEN onCatalogClick(CatalogId("cat-1"), "Mathematics")
    // THEN quizzesComponent.openQuestList called with (CatalogId("cat-1"), "Mathematics")

    @Test
    fun `WIRE-01 homeQuestsComponent onCatalogClick triggers quizzesComponent openQuestList`() = runTest {
        val captured = CapturedQuizzesComponent()

        val root = buildWiringComponent(capturedQuizzes = captured)

        root.homeQuestsComponent.onCatalogClick(CatalogId("cat-1"), "Mathematics")

        val args = captured.openQuestListArgs
        assertEquals(CatalogId("cat-1"), args?.catalogId, "openQuestList must receive the clicked catalogId")
        assertEquals("Mathematics", args?.catalogName, "openQuestList must receive the resolved catalogName")
    }

    // ── WIRE-02 — MyQuestsComponent.onQuestClick triggers QuizzesComponent.openSectionList ──
    // Spec: 04-testing.md §10 INT-02
    // GIVEN quest with id="q-1", catalogId="cat-1"
    // WHEN rootComponent.myQuestsComponent.onQuestClick(quest)
    // THEN quizzesComponent.openSectionList called with questId="q-1" in args

    @Test
    fun `WIRE-02 myQuestsComponent onQuestClick triggers quizzesComponent openSectionList`() = runTest {
        val captured = CapturedQuizzesComponent()
        val root = buildWiringComponent(capturedQuizzes = captured)
        val quest = QuestDisplayItem(
            id = QuestId("q-1"),
            catalogId = CatalogId("cat-1"),
            title = "Quest A",
            pictureUrl = null,
            averageRating = null,
        )

        root.myQuestsComponent.onQuestClick(quest)

        val args = captured.openSectionListArgs
        assertEquals(QuestId("q-1"), args?.questId, "openSectionList must receive the quest id")
    }

    // ── WIRE-03 — catalogName in openSectionList resolved from homeQuestsComponent catalogs ──
    // Spec: 04-testing.md §10 WIRE-03
    // GIVEN homeQuestsComponent has catalog CatalogId("cat-1") → name="Mathematics"
    // GIVEN quest.catalogId = CatalogId("cat-1"), quest.title = "Quest A"
    // WHEN myQuestsComponent.onQuestClick(quest)
    // THEN openSectionList(quest.id, titles) where titles[0]=="Mathematics" and titles[1]=="Quest A"

    @Test
    fun `WIRE-03 catalogName in openSectionList resolved from homeQuestsComponent state catalogs`() = runTest {
        val catalogRepo = FakeCatalogRepository()
        catalogRepo.emit(listOf(buildCatalog(id = "cat-1", name = "Mathematics")))
        val captured = CapturedQuizzesComponent()

        val root = buildWiringComponent(catalogRepo = catalogRepo, capturedQuizzes = captured)
        val quest = QuestDisplayItem(
            id = QuestId("q-1"),
            catalogId = CatalogId("cat-1"),
            title = "Quest A",
            pictureUrl = null,
            averageRating = null,
        )

        root.myQuestsComponent.onQuestClick(quest)

        val titles = captured.openSectionListArgs?.titles
        assertEquals("Mathematics", titles?.getOrNull(0),
            "first title must be catalog name resolved from homeQuestsComponent state")
        assertEquals("Quest A", titles?.getOrNull(1),
            "second title must be quest title")
    }

    // ── WIRE-03 fallback — catalogs empty → titles[0] is blank (no crash) ─────────
    // Spec: 04-testing.md §10 WIRE-03 edge case
    // DefaultRootComponent passes an empty catalog name when the catalog is not found;
    // the UI layer renders the localized placeholder for blank names.

    @Test
    fun `WIRE-03 fallback when catalogs empty then catalogName is blank and no crash`() = runTest {
        val catalogRepo = FakeCatalogRepository()
        // no emit → empty catalogs
        val captured = CapturedQuizzesComponent()

        val root = buildWiringComponent(catalogRepo = catalogRepo, capturedQuizzes = captured)
        val quest = QuestDisplayItem(
            id = QuestId("q-2"),
            catalogId = CatalogId("cat-unknown"),
            title = "Quest B",
            pictureUrl = null,
            averageRating = null,
        )

        root.myQuestsComponent.onQuestClick(quest)

        val titles = captured.openSectionListArgs?.titles
        assertEquals("", titles?.getOrNull(0),
            "fallback catalogName must be blank when catalog is not found")
        assertEquals("Quest B", titles?.getOrNull(1),
            "quest title must still be included in titles")
    }

    // ── WIRE-04 — dismissQuizzes hides overlay ────────────────────────────────
    // Spec: 04-testing.md §10 INT-03
    // GIVEN quizzesComponent in active state (QuestList shown)
    // WHEN quizzesComponent.dismissQuizzes()
    // THEN active instance is QuizzesChild.Idle

    @Test
    fun `WIRE-04 dismissQuizzes makes overlay return to Idle state`() = runTest {
        val catalogRepo = FakeCatalogRepository()
        catalogRepo.emit(listOf(buildCatalog(id = "cat-1", name = "Mathematics")))
        val captured = CapturedQuizzesComponent()

        val root = buildWiringComponent(catalogRepo = catalogRepo, capturedQuizzes = captured)

        // Open overlay first
        root.homeQuestsComponent.onCatalogClick(CatalogId("cat-1"), "Mathematics")
        assertTrue(
            captured.childStack.value.active.instance is QuizzesChild.QuestList,
            "pre-condition: QuizzesChild.QuestList must be active after onCatalogClick",
        )

        // Dismiss
        root.quizzesComponent.dismissQuizzes()

        assertTrue(
            captured.childStack.value.active.instance is QuizzesChild.Idle,
            "after dismissQuizzes, active instance must be QuizzesChild.Idle",
        )
        assertTrue(captured.dismissCalled, "dismissCalled flag must be set on captured component")
    }

    // ── INT-04 — Root backCallback has lower priority than QuizzesComponent backCallback ──
    // Spec: 04-testing.md §10 INT-04
    // GIVEN DefaultRootComponent with real DefaultQuizzesComponent using TestBackHandler
    // WHEN both components register BackCallbacks on the shared BackHandler
    // THEN root's callback priority (0, default) < quizzes callback priority (100)
    //
    // Production:
    //   DefaultRootComponent.init: backHandler.register(BackCallback(isEnabled = true))       → priority 0
    //   DefaultQuizzesComponent.init: backHandler.register(BackCallback(priority = 100, ...)) → priority 100

    @Test
    fun `INT-04 root backCallback registered with lower priority than QuizzesComponent backCallback`() = runTest {
        val testBackHandler = TestBackHandler()
        buildWithRealQuizzes(backHandler = testBackHandler)

        val callbacks = testBackHandler.registeredCallbacks
        assertTrue(callbacks.size >= 2,
            "Expected at least 2 registered callbacks: root (priority 0) + quizzes (priority 100); got ${callbacks.size}")

        val quizzesCallback = callbacks.find { it.priority == 100 }
        assertNotNull(quizzesCallback, "QuizzesComponent must register BackCallback with priority == 100")

        val rootCallback = callbacks.find { it.priority < 100 }
        assertNotNull(rootCallback, "Root must register BackCallback with priority < 100")

        assertTrue(rootCallback.priority < quizzesCallback.priority,
            "Root backCallback priority (${rootCallback.priority}) must be strictly less than quizzes priority (${quizzesCallback.priority})")
    }

    // ── INT-05 — QuizzesComponent backCallback disabled in Idle → Root handles Back ──
    // Spec: 04-testing.md §10 INT-05
    // GIVEN DefaultRootComponent with real DefaultQuizzesComponent using BackDispatcher
    // WHEN QuizzesComponent childStack is Idle (its BackCallback is disabled)
    // AND BackDispatcher.back() is invoked
    // THEN back() returns true — Root's BackCallback (priority 0, isEnabled=true) handles Back
    //
    // DefaultQuizzesComponent: BackCallback(priority=100, isEnabled=false initially);
    // only enabled when backStack.isNotEmpty(). In Idle state → backStack is empty → disabled.
    // DefaultRootComponent: BackCallback(isEnabled=true) — always enabled, handles Back via FSM.

    @Test
    fun `INT-05 when QuizzesChild is Idle root backCallback handles Back event`() = runTest {
        val backDispatcher = BackDispatcher()
        val root = buildWithRealQuizzes(backHandler = backDispatcher)

        // Verify pre-condition: QuizzesChild is Idle
        assertTrue(
            root.quizzesComponent.childStack.value.active.instance is QuizzesChild.Idle,
            "pre-condition: QuizzesComponent must start in Idle state",
        )

        // Dispatch back: quizzes callback disabled (Idle) → root callback (priority 0, enabled) fires
        val handled = backDispatcher.back()

        assertTrue(handled,
            "BackDispatcher.back() must return true when root BackCallback (isEnabled=true) handles Back in Idle state")
    }
}

private class OfflineProfileRepository : ProfileRepository {
    override fun observeCurrentProfile(): Flow<UserProfile> = flowOf(UserProfile.offline())

    override suspend fun currentProfile(): UserProfile = UserProfile.offline()

    override suspend fun ensureCurrentProfile(): Result<UserProfile> = Result.success(UserProfile.offline())

    override suspend fun updateNickname(nickname: String): Result<UserProfile> =
        Result.success(UserProfile.offline().copy(nickname = nickname))
}

private object NoGiftBoxRepository : GiftBoxRepository {
    override suspend fun openGiftBox(): Result<GiftBoxOpening> =
        Result.failure(IllegalStateException("No gift boxes available"))
}
