@file:Suppress("MaxLineLength")

package com.tpov.schoolquiz.android.feature.app_shell.presentation

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.lifecycle.stop
import com.tpov.schoolquiz.android.feature.app_shell.presentation.component.DefaultRootComponent
import com.tpov.schoolquiz.android.feature.app_shell.presentation.component.TournamentOverviewLoadState
import com.tpov.schoolquiz.android.feature.app_shell.presentation.fake.FakeSyncScheduler
import com.tpov.schoolquiz.android.feature.app_shell.presentation.fake.FakeUserStatsRepository
import com.tpov.schoolquiz.android.feature.app_shell.presentation.fake.StubHomeQuestsComponent
import com.tpov.schoolquiz.android.feature.app_shell.presentation.fake.StubMyQuestsComponent
import com.tpov.schoolquiz.android.feature.app_shell.presentation.fake.StubQuizzesComponent
import com.tpov.schoolquiz.android.feature.app_shell.presentation.R
import com.tpov.schoolquiz.android.feature.app_shell.presentation.ui.labels.labelRes
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Destination
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.DrawerSection
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.LocalConfig
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.RetapOutcome
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.RootEvent
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Tab
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.UserStats
import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.UserStatsRepository
import com.tpov.schoolquiz.shared.feature.app_shell.domain.state.AppShellState
import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.InitializeAppShellUseCase
import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.NavigateUseCase
import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.ObserveAppShellStateUseCase
import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.OnTabRetapUseCase
import com.tpov.schoolquiz.shared.feature.internet.leaderboard.domain.model.TournamentOverview
import com.tpov.schoolquiz.shared.feature.internet.leaderboard.domain.model.TournamentSummary
import com.tpov.schoolquiz.shared.feature.internet.leaderboard.domain.repository.TournamentLeaderboardRepository
import com.tpov.schoolquiz.shared.feature.internet.leaderboard.domain.use_case.FetchTournamentOverviewUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Integration tests for [DefaultRootComponent] via [TestComponentContext].
 *
 * Covers all FSM axes from State Matrix Coverage (overview.md):
 * - Back 4-step FSM (steps 1, 3, 4)
 * - RetapOutcome FSM (NO_OP)
 * - Cold Start FSM
 * - DrawerGuard (SHOP no drawer)
 * - Tab switch FSM
 * - Drawer section switch FSM (cross-tab, hidden-section guard)
 * - SectionVisibility guard
 *
 * Each test is traced to a specific GIVEN/WHEN/THEN from 0-spec.md / overview.md Tests Required.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultRootComponentTest {

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

    private fun testCtx(): DefaultComponentContext {
        lifecycle = LifecycleRegistry()
        lifecycle.resume()
        return DefaultComponentContext(lifecycle)
    }

    private fun buildComponent(
        fakeRepo: FakeUserStatsRepository = FakeUserStatsRepository(),
        syncScheduler: FakeSyncScheduler = FakeSyncScheduler(),
        fetchTournamentOverview: FetchTournamentOverviewUseCase = fakeTournamentOverviewUseCase(),
    ) = DefaultRootComponent(
        componentContext = testCtx(),
        initUseCase = InitializeAppShellUseCase(fakeRepo),
        navigateUseCase = NavigateUseCase(),
        observeUseCase = ObserveAppShellStateUseCase(fakeRepo),
        retapUseCase = OnTabRetapUseCase(),
        fetchTournamentOverview = fetchTournamentOverview,
        userStatsRepository = fakeRepo,
        syncScheduler = syncScheduler,
        myQuestsFactory = { _, _, _ -> StubMyQuestsComponent },
        homeQuestsFactory = { _, _ -> StubHomeQuestsComponent },
        quizzesFactory = { _ -> StubQuizzesComponent },
        // Keep the IO hop on the test scheduler. With the real Dispatchers.IO the scheduler goes
        // idle, runTest's virtual clock jumps ahead, and withTimeout fires before the background
        // thread answers — the tournament overview test then failed only on a loaded machine.
        ioContext = testDispatcher,
    )

    private fun fakeTournamentOverviewUseCase(
        onFetch: (String) -> Unit = {},
    ): FetchTournamentOverviewUseCase =
        FetchTournamentOverviewUseCase(
            object : TournamentLeaderboardRepository {
                override suspend fun fetchOverview(
                    tournamentId: String,
                    limit: Int,
                ): Result<TournamentOverview> {
                    onFetch(tournamentId)
                    return Result.success(
                        TournamentOverview(
                            tournament =
                                TournamentSummary(
                                    id = tournamentId,
                                    sourceShelf = tournamentId,
                                    title = "Tournament",
                                    stageLabel = "Stage",
                                    updatedAtMs = 1L,
                                    leaderboardUpdatedAtMs = 1L,
                                ),
                            metadata = null,
                            leaderboard = emptyList(),
                            participants = emptyList(),
                            currentUserEntry = null,
                            currentUserParticipant = null,
                        ),
                    )
                }
            },
        )

    // -----------------------------------------------------------------------
    // Cold Start FSM (R1)
    // Spec: cold_start_state_equals_init_use_case_result
    // GIVEN DefaultRootComponent with FakeUserStatsRepository(currentSkill=100)
    // WHEN component initialized
    // THEN appShellState.activeTab == LOCAL (spec FR #6) and userStats.currentSkill == 100
    // -----------------------------------------------------------------------

    @Test
    fun `cold start state equals AppShellState default with fetched stats`() = runTest(testDispatcher) {
        val stats = UserStats.guest().copy(currentSkill = 100)
        val fakeRepo = FakeUserStatsRepository(stats)

        val component = buildComponent(fakeRepo)

        val state = component.appShellState.first()
        assertEquals(Tab.LOCAL, state.activeTab)
        assertEquals(100, state.userStats.currentSkill)
    }

    // -----------------------------------------------------------------------
    // Cold Start FSM — error path
    // Spec: init_use_case_throws_then_fallback_state
    // GIVEN DefaultRootComponent where currentStats() throws RuntimeException
    // WHEN component initialized
    // THEN appShellState == AppShellState.fallback(UserStats.guest())
    // -----------------------------------------------------------------------

    @Test
    fun `init use case throws then fallback state is used`() = runTest(testDispatcher) {
        val errorRepo = object : UserStatsRepository {
            override fun observeStats(): Flow<UserStats> = MutableStateFlow(UserStats.guest()).asStateFlow()
            override suspend fun currentStats(): UserStats = error("offline")
            override suspend fun setLocalDeveloperLevel(value: Int) = Unit
            override suspend fun refreshProfile(): Result<Unit> = Result.success(Unit)
        }
        val component = DefaultRootComponent(
            componentContext = testCtx(),
            initUseCase = InitializeAppShellUseCase(errorRepo),
            navigateUseCase = NavigateUseCase(),
            observeUseCase = ObserveAppShellStateUseCase(FakeUserStatsRepository()),
            retapUseCase = OnTabRetapUseCase(),
            userStatsRepository = errorRepo,
            syncScheduler = FakeSyncScheduler(),
            myQuestsFactory = { _, _, _ -> StubMyQuestsComponent },
            homeQuestsFactory = { _, _ -> StubHomeQuestsComponent },
            quizzesFactory = { _ -> StubQuizzesComponent },
        )

        val state = component.appShellState.first()
        assertEquals(AppShellState.fallback(UserStats.guest()), state)
    }

    // -----------------------------------------------------------------------
    // Tab switch FSM
    // Spec: go_to_switch_tab_changes_active_tab
    // GIVEN DefaultRootComponent in LOCAL state
    // WHEN onDestination(SwitchTab(INTERNET))
    // THEN appShellState.activeTab == INTERNET
    // -----------------------------------------------------------------------

    @Test
    fun `go to SwitchTab changes activeTab`() = runTest(testDispatcher) {
        val component = buildComponent()

        component.onDestination(Destination.SwitchTab(Tab.INTERNET))

        assertEquals(Tab.INTERNET, component.appShellState.first().activeTab)
    }

    // -----------------------------------------------------------------------
    // Stats observe — navigation preservation (ADR-LEAD-02)
    // Spec: observe_stats_emits_then_navigation_preserved
    // GIVEN DefaultRootComponent switched to INTERNET
    // WHEN new stats emitted by FakeUserStatsRepository
    // THEN activeTab still INTERNET; userStats.currentSkill reflects new emission
    // -----------------------------------------------------------------------

    @Test
    fun `when stats emit after navigation change then navigation state is preserved`() = runTest(testDispatcher) {
        val fakeRepo = FakeUserStatsRepository()
        val component = buildComponent(fakeRepo)

        component.onDestination(Destination.SwitchTab(Tab.INTERNET))
        fakeRepo.emit(UserStats.guest().copy(currentSkill = 999))

        val state = component.appShellState.first()
        assertEquals(Tab.INTERNET, state.activeTab)
        assertEquals(999, state.userStats.currentSkill)
    }

    // -----------------------------------------------------------------------
    // Back FSM — step 1: drawer open → close drawer
    // Spec: back_with_drawer_open_closes_drawer (Journey 10)
    // GIVEN DefaultRootComponent, drawer open (isDrawerOpen == true)
    // WHEN onDestination(Back)
    // THEN isDrawerOpen == false; activeTab unchanged (LOCAL)
    // -----------------------------------------------------------------------

    @Test
    fun `back with drawer open closes drawer`() = runTest(testDispatcher) {
        val component = buildComponent()
        component.onDestination(Destination.OpenDrawer)
        assertTrue(component.appShellState.first().isDrawerOpen)

        component.onDestination(Destination.Back)

        val state = component.appShellState.first()
        assertFalse(state.isDrawerOpen)
        assertEquals(Tab.LOCAL, state.activeTab)
    }

    // -----------------------------------------------------------------------
    // Back FSM — step 3: empty backStack + tab != LOCAL → switch to LOCAL
    // Spec: Journey 4 step 2
    // GIVEN DefaultRootComponent on INTERNET tab (empty backStack)
    // WHEN onDestination(Back)
    // THEN activeTab == LOCAL
    // -----------------------------------------------------------------------

    @Test
    fun `back on non-LOCAL root with empty backStack switches to LOCAL`() = runTest(testDispatcher) {
        val component = buildComponent()
        component.onDestination(Destination.SwitchTab(Tab.INTERNET))
        assertEquals(Tab.INTERNET, component.appShellState.first().activeTab)

        component.onDestination(Destination.Back)

        assertEquals(Tab.LOCAL, component.appShellState.first().activeTab)
    }

    // -----------------------------------------------------------------------
    // Back FSM — step 4: empty backStack + LOCAL → emit RootEvent.SystemBack
    // Spec: back_on_LOCAL_root_emits_system_back (Journey 4 step 3)
    // GIVEN DefaultRootComponent on LOCAL tab at root (backStack empty, drawer closed)
    // WHEN onDestination(Back)
    // THEN events flow emits RootEvent.SystemBack
    // -----------------------------------------------------------------------

    @Test
    fun `back on LOCAL root emits SystemBack`() = runTest(testDispatcher) {
        val component = buildComponent()
        val collectedEvents = mutableListOf<RootEvent>()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            component.events.toList(collectedEvents)
        }

        component.onDestination(Destination.Back)
        job.cancel()

        assertTrue(collectedEvents.any { it == RootEvent.SystemBack })
    }

    // -----------------------------------------------------------------------
    // RetapOutcome FSM — row 2: backStack empty → NO_OP
    // Spec: retap_at_root_returns_NO_OP (Journey 6)
    // GIVEN DefaultRootComponent on LOCAL at root (backStack empty)
    // WHEN onActiveTabRetap(LOCAL)
    // THEN RetapOutcome == NO_OP
    // -----------------------------------------------------------------------

    @Test
    fun `retap at root returns NO_OP`() = runTest(testDispatcher) {
        val component = buildComponent()

        val outcome = component.onActiveTabRetap(Tab.LOCAL)

        assertEquals(RetapOutcome.NO_OP, outcome)
    }

    // -----------------------------------------------------------------------
    // DrawerGuard (SHOP tab)
    // Spec: open_drawer_shop_no_op (spec FR #3)
    // GIVEN DefaultRootComponent, activeTab == SHOP
    // WHEN onDestination(OpenDrawer)
    // THEN isDrawerOpen == false (SHOP never has drawer)
    // -----------------------------------------------------------------------

    @Test
    fun `open drawer on SHOP tab is no-op`() = runTest(testDispatcher) {
        val component = buildComponent()
        component.onDestination(Destination.SwitchTab(Tab.SHOP))
        assertEquals(Tab.SHOP, component.appShellState.first().activeTab)

        component.onDestination(Destination.OpenDrawer)

        assertFalse(component.appShellState.first().isDrawerOpen)
    }

    // -----------------------------------------------------------------------
    // SectionVisibility guard (AC 23g)
    // Spec: go_to_select_section_hidden_section_no_op
    // GIVEN DefaultRootComponent with guest stats (currentSkill == 0)
    //       Arena requires USER >= Title.TEACHINGS.first (3000)
    // WHEN onDestination(SelectSection(InternetSection.Arena))
    // THEN state unchanged — domain visibility guard blocks hidden section
    // -----------------------------------------------------------------------

    @Test
    fun `select hidden section is no-op due to visibility guard`() = runTest(testDispatcher) {
        val fakeRepo = FakeUserStatsRepository(UserStats.guest())  // currentSkill = 0
        val component = buildComponent(fakeRepo)
        val stateBefore = component.appShellState.first()

        // Arena.requiredRoles = mapOf(Role.USER to Title.TEACHINGS.first = 3000)
        // guest has currentSkill = 0 → guard blocks
        component.onDestination(Destination.SelectSection(DrawerSection.InternetSection.Arena))

        val stateAfter = component.appShellState.first()
        assertEquals(stateBefore.activeTab, stateAfter.activeTab)
        assertEquals(stateBefore.activeSection, stateAfter.activeSection)
    }

    // -----------------------------------------------------------------------
    // Drawer section switch FSM — cross-tab auto-switch (Row 1)
    // Spec: select_section_cross_tab_auto_switches
    // GIVEN DefaultRootComponent on LOCAL tab
    // WHEN onDestination(SelectSection(InternetSection.Profile))
    //   InternetSection.Profile has emptyMap requiredRoles — always visible
    // THEN activeTab == INTERNET and activeSection == Profile
    // -----------------------------------------------------------------------

    @Test
    fun `select section from different tab auto-switches tab`() = runTest(testDispatcher) {
        val component = buildComponent()  // starts on LOCAL
        assertEquals(Tab.LOCAL, component.appShellState.first().activeTab)

        component.onDestination(Destination.SelectSection(DrawerSection.InternetSection.Profile))

        val state = component.appShellState.first()
        assertEquals(Tab.INTERNET, state.activeTab)
        assertEquals(DrawerSection.InternetSection.Profile, state.activeSection)
    }

    // -----------------------------------------------------------------------
    // OpenDesignCatalog destination
    // Spec: open_design_catalog_switches_to_LOCAL (spec FR #17)
    // GIVEN DefaultRootComponent on INTERNET tab
    // WHEN onDestination(OpenDesignCatalog)
    // THEN activeTab == LOCAL, localState.stack.active == DesignCatalogRoot, isDrawerOpen == false
    // -----------------------------------------------------------------------

    @Test
    fun `open design catalog switches to LOCAL with DesignCatalogRoot`() = runTest(testDispatcher) {
        val component = buildComponent()
        component.onDestination(Destination.SwitchTab(Tab.INTERNET))

        component.onDestination(Destination.OpenDesignCatalog)

        val state = component.appShellState.first()
        assertEquals(Tab.LOCAL, state.activeTab)
        assertEquals(LocalConfig.DesignCatalogRoot, state.localState.stack.active)
        assertFalse(state.isDrawerOpen)
    }

    // -----------------------------------------------------------------------
    // Cold Start FSM — race: navigation happens before init completes
    // Spec: cold_start_race_navigation_not_overwritten (cross-phase review HIGH fix)
    // GIVEN DefaultRootComponent with slow currentStats() (deferred)
    // WHEN user navigates to INTERNET tab BEFORE initUseCase() resolves
    // THEN after init resolves: navigation is preserved, userStats is merged
    // -----------------------------------------------------------------------

    @Test
    fun `cold start race when navigation happens before init then navigation is preserved`() = runTest(testDispatcher) {
        val initDeferred = CompletableDeferred<UserStats>()
        val fakeRepo = object : UserStatsRepository {
            private val _stats = MutableStateFlow(UserStats.guest())
            override fun observeStats(): Flow<UserStats> = _stats.asStateFlow()
            override suspend fun currentStats(): UserStats = initDeferred.await()
            override suspend fun setLocalDeveloperLevel(value: Int) = Unit
            override suspend fun refreshProfile(): Result<Unit> = Result.success(Unit)
        }
        val component = DefaultRootComponent(
            componentContext = testCtx(),
            initUseCase = InitializeAppShellUseCase(fakeRepo),
            navigateUseCase = NavigateUseCase(),
            observeUseCase = ObserveAppShellStateUseCase(fakeRepo),
            retapUseCase = OnTabRetapUseCase(),
            userStatsRepository = fakeRepo,
            syncScheduler = FakeSyncScheduler(),
            myQuestsFactory = { _, _, _ -> StubMyQuestsComponent },
            homeQuestsFactory = { _, _ -> StubHomeQuestsComponent },
            quizzesFactory = { _ -> StubQuizzesComponent },
        )

        // Navigate while initUseCase() is suspended at initDeferred.await()
        component.onDestination(Destination.SwitchTab(Tab.INTERNET))
        assertEquals(Tab.INTERNET, component.appShellState.first().activeTab)

        // Resolve init — init path must NOT overwrite the navigation change
        initDeferred.complete(UserStats.guest().copy(currentSkill = 777))
        testScheduler.advanceUntilIdle()

        val state = component.appShellState.first()
        assertEquals(Tab.INTERNET, state.activeTab)        // navigation preserved
        assertEquals(777, state.userStats.currentSkill)    // userStats merged
    }

    // -----------------------------------------------------------------------
    // OpenQuestCreate destination (phase-04 / AC#29 / scenarios 41a+41b guard)
    // Spec: when_onDestination_OpenQuestCreate_then_no_crash
    // GIVEN DefaultRootComponent in initial LOCAL state (HomeQuestsRoot)
    // WHEN onDestination(OpenQuestCreate)
    // THEN activeTab stays LOCAL; QuestCreateRoot becomes active; backStack.size == 1
    // -----------------------------------------------------------------------

    @Test
    fun `when onDestination OpenQuestCreate then QuestCreateRoot becomes active without crash`() = runTest(testDispatcher) {
        val component = buildComponent()

        component.onDestination(Destination.OpenQuestCreate)

        val state = component.appShellState.first()
        assertEquals(Tab.LOCAL, state.activeTab)
        assertEquals(LocalConfig.QuestCreateRoot, state.localState.stack.active)
        assertEquals(1, state.localState.stack.backStack.size)
    }

    // -----------------------------------------------------------------------
    // OpenQuestCreate guard — Decision #47 (scenario 41b guard)
    // Spec: when_onDestination_OpenQuestCreate_twice_then_guard_applied
    // GIVEN component after first OpenQuestCreate (QuestCreateRoot active)
    // WHEN onDestination(OpenQuestCreate) again
    // THEN state unchanged — backStack still has exactly 1 entry (no duplicate)
    // -----------------------------------------------------------------------

    @Test
    fun `when onDestination OpenQuestCreate twice then guard applied no duplicate backStack entry`() = runTest(testDispatcher) {
        val component = buildComponent()
        component.onDestination(Destination.OpenQuestCreate)
        val stateAfterFirst = component.appShellState.first()

        component.onDestination(Destination.OpenQuestCreate)
        val stateAfterSecond = component.appShellState.first()

        assertEquals(stateAfterFirst, stateAfterSecond)
        assertEquals(LocalConfig.QuestCreateRoot, stateAfterSecond.localState.stack.active)
        assertEquals(1, stateAfterSecond.localState.stack.backStack.size)
    }

    @Test
    fun `open qualifier leaderboard fetches tournament overview`() = runTest(testDispatcher) {
        val requestedTournamentIds = mutableListOf<String>()
        val component =
            buildComponent(
                fetchTournamentOverview =
                    fakeTournamentOverviewUseCase { tournamentId ->
                        requestedTournamentIds += tournamentId
                    },
            )

        component.onDestination(Destination.OpenTournamentLeaderboard(DrawerSection.EventsSection.QualifierTournament))

        val overviewState =
            withTimeout(5_000L) {
                component.tournamentOverviewState.first {
                    it["tournament"] is TournamentOverviewLoadState.Content
                }
            }
        assertEquals(listOf("tournament"), requestedTournamentIds)
        assertTrue(overviewState["tournament"] is TournamentOverviewLoadState.Content)
    }

    // -----------------------------------------------------------------------
    // Labels.kt exhaustive when — scenario 41e
    // Spec: Labels_labelRes_QuestCreateRoot_non_blank
    // GIVEN LocalConfig.QuestCreateRoot
    // WHEN labelRes property accessed
    // THEN maps to the quest-create screen title resource
    // (compile-time: exhaustive `when` in Labels.kt ensures QuestCreateRoot is handled)
    // -----------------------------------------------------------------------

    @Test
    fun `Labels displayName QuestCreateRoot returns non-blank string`() {
        val res = LocalConfig.QuestCreateRoot.labelRes
        assertEquals(R.string.config_quest_create, res)
    }

    // -----------------------------------------------------------------------
    // Back FSM — step 2: non-empty backStack → pop (now testable via OpenQuestCreate)
    // Spec: back_with_non_empty_backStack_pops (phase-04: backStack filled by OpenQuestCreate)
    // GIVEN component after OpenQuestCreate (QuestCreateRoot active, backStack=[HomeQuestsRoot])
    // WHEN onDestination(Back)
    // THEN stack returns to HomeQuestsRoot and backStack is empty
    // -----------------------------------------------------------------------

    @Test
    fun `back with non-empty backStack pops top entry`() = runTest(testDispatcher) {
        val component = buildComponent()
        component.onDestination(Destination.OpenQuestCreate)
        assertEquals(LocalConfig.QuestCreateRoot, component.appShellState.first().localState.stack.active)

        component.onDestination(Destination.Back)

        val state = component.appShellState.first()
        assertTrue(state.localState.stack.backStack.isEmpty())
        assertTrue(state.localState.stack.active != LocalConfig.QuestCreateRoot)
    }

    // DEFERRED: retap_with_backStack_returns_POP_TO_ROOT
    // Fix under test: onActiveTabRetap must call syncStack after POP_TO_ROOT so Decompose
    // childStack reflects the cleared backStack (before fix: syncStack was omitted).
    // Remaining blocker: no push destinations in MVP — backStack is always empty.
    // When Destination.Push is added: remove @Ignore, implement setup via Push, run.
    @Ignore("Deferred: push not in MVP — remove when Destination.Push available")
    @Test
    fun `retap with non-empty backStack returns POP TO ROOT`() = runTest(testDispatcher) {
        // Pre-condition: push something to create a non-empty backStack.
        // When Destination.Push(config) is available:
        //   component.onDestination(Destination.Push(LocalConfig.HomeQuestsRoot))
        val component = buildComponent()

        val outcome = component.onActiveTabRetap(Tab.LOCAL)

        // With non-empty backStack: expect POP_TO_ROOT
        assertEquals(RetapOutcome.POP_TO_ROOT, outcome)
        // Decompose sync fix: onActiveTabRetap MUST call syncStack after retap so that
        // localTabComponent.childStack reflects the cleared backStack:
        assertEquals(
            LocalConfig.HomeQuestsRoot,
            component.localTabComponent.childStack.value.active.configuration,
        )
        assertEquals(0, component.localTabComponent.childStack.value.backStack.size)
    }
}
