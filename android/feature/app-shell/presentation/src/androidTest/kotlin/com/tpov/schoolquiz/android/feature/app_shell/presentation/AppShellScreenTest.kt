package com.tpov.schoolquiz.android.feature.app_shell.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tpov.schoolquiz.android.feature.app_shell.presentation.R
import com.arkivanov.decompose.Child
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.lifecycle.stop
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
import com.tpov.schoolquiz.android.core.designsystem.components.SchoolQuizDesignStyle
import com.tpov.schoolquiz.android.core.designsystem.model.QuestDisplayItem
import com.tpov.schoolquiz.android.feature.app_shell.presentation.component.DefaultRootComponent
import com.tpov.schoolquiz.android.feature.app_shell.presentation.ui.AppShellScreen
import com.tpov.schoolquiz.android.feature.app_shell.presentation.ui.TournamentEventActions
import com.tpov.schoolquiz.android.feature.app_shell.presentation.ui.TournamentEventScreen
import com.tpov.schoolquiz.android.feature.app_shell.presentation.ui.TournamentEventUi
import com.tpov.schoolquiz.android.feature.app_shell.presentation.ui.UnderConstructionScreen
import com.tpov.schoolquiz.android.feature.app_shell.presentation.ui.scroll.LocalScrollToTopRegistry
import com.tpov.schoolquiz.android.feature.app_shell.presentation.ui.scroll.ScrollToTopRegistry
import com.tpov.schoolquiz.android.feature.local.settings.presentation.ui.NoirSettingsScreen
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.UserProfile
import com.tpov.schoolquiz.android.feature.quest.presentation.DraftQuestDisplayItem
import com.tpov.schoolquiz.android.feature.quest.presentation.HomeQuestsComponent
import com.tpov.schoolquiz.android.feature.quest.presentation.HomeQuestsUiState
import com.tpov.schoolquiz.android.feature.quest.presentation.MyQuestsComponent
import com.tpov.schoolquiz.android.feature.quest.presentation.MyQuestsUiState
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesChild
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesComponent
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.BreadcrumbRoot
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.core.sync.SyncFrequency
import com.tpov.schoolquiz.shared.core.sync.SyncScheduler
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.UserStats
import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.UserStatsRepository
import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.InitializeAppShellUseCase
import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.NavigateUseCase
import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.ObserveAppShellStateUseCase
import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.OnTabRetapUseCase
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import androidx.compose.ui.unit.height

/**
 * Compile-level instrumented tests for phase-05 AppShellScreen composables.
 *
 * Spec traceability (docs/features/app-shell-menu/plan/phase-05/tests.md):
 *  - AC 13: UnderConstructionScreen renders title and subtitle
 *  - scroll_registry_provided_in_composition
 *  - snapshot_flow_drawer_sync_compiles
 *
 * Note: full hamburger/drawer FSM assertion deferred to phase-07 manual smoke (AC 29).
 */
@RunWith(AndroidJUnit4::class)
class AppShellScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun string(res: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(res)

    // AC 13: GIVEN SchoolQuizTheme applied WHEN UnderConstructionScreen shown
    // THEN both title and subtitle text are displayed
    @Test
    fun under_construction_screen_renders_title_and_subtitle() {
        composeTestRule.setContent {
            SchoolQuizTheme {
                UnderConstructionScreen(title = "Test Screen")
            }
        }
        composeTestRule.onNodeWithText("Test Screen").assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.under_construction_subtitle)).assertIsDisplayed()
    }

    // GIVEN CompositionLocalProvider with ScrollToTopRegistry
    // WHEN LocalScrollToTopRegistry.current accessed THEN registry is not null
    @Test
    fun scroll_registry_provided_in_composition() {
        var registry: ScrollToTopRegistry? = null
        composeTestRule.setContent {
            SchoolQuizTheme {
                val r = remember { ScrollToTopRegistry() }
                CompositionLocalProvider(LocalScrollToTopRegistry provides r) {
                    registry = LocalScrollToTopRegistry.current
                    Box {}
                }
            }
        }
        assertNotNull(registry)
    }

    // GIVEN rememberDrawerState WHEN LaunchedEffect collects snapshotFlow
    // THEN composition succeeds without crash (compile-level verification of drawer sync pattern)
    @Test
    fun snapshot_flow_drawer_sync_compiles() {
        var drawerSeen = false
        composeTestRule.setContent {
            val drawerState = rememberDrawerState(DrawerValue.Closed)
            LaunchedEffect(Unit) {
                snapshotFlow { drawerState.currentValue }.collect { drawerSeen = true }
            }
            Box {}
        }
        composeTestRule.waitForIdle()
        assertTrue(drawerSeen)
    }

    @Test
    fun tournament_event_screen_renders_start_button_and_developer_fab() {
        composeTestRule.setContent {
            SchoolQuizTheme {
                TournamentEventScreen(
                    model =
                        TournamentEventUi(
                            title = "Отборочный турнир",
                            stageLabel = "Лёгкие вопросы",
                        ),
                    actions =
                        TournamentEventActions(
                            canManagePublicShelves = true,
                            onStartClick = {},
                            onLeaderboardClick = {},
                            onLessonsClick = {},
                            onParticipantsClick = {},
                            onAddLessonsClick = {},
                        ),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        composeTestRule.onNodeWithText(string(R.string.tournament_start)).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(string(R.string.cd_add_lesson)).assertIsDisplayed()
    }

    @Test
    fun tournament_event_screen_hides_fab_for_non_developer() {
        composeTestRule.setContent {
            SchoolQuizTheme {
                TournamentEventScreen(
                    model =
                        TournamentEventUi(
                            title = "Отборочный турнир",
                            stageLabel = "Лёгкие вопросы",
                        ),
                    actions =
                        TournamentEventActions(
                            canManagePublicShelves = false,
                            onStartClick = {},
                            onLeaderboardClick = {},
                            onLessonsClick = {},
                            onParticipantsClick = {},
                            onAddLessonsClick = {},
                        ),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        composeTestRule.onNodeWithText(string(R.string.tournament_start)).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(string(R.string.cd_add_lesson)).assertDoesNotExist()
    }

    // SCH-2: settings_footer_exact_label
    // GIVEN settings UI receives appVersionName="0.1.0" and appVersionCode=1
    // WHEN the settings screen is rendered
    // THEN the footer displays exactly "v0.1.0 (1)".
    @Test
    fun settings_footer_displays_exact_version_label() {
        composeTestRule.setContent {
            SchoolQuizTheme {
                NoirSettingsScreen(
                    profile = UserProfile.offline(),
                    appVersionName = "0.1.0",
                    appVersionCode = 1,
                    onSyncNow = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        composeTestRule.onNodeWithText("v0.1.0 (1)").assertIsDisplayed()
    }

    // SCH-2: settings_footer_display_only_semantics
    // GIVEN the settings footer is displayed
    // WHEN its semantics are inspected
    // THEN it has no click or long-click action.
    @Test
    fun settings_footer_is_display_only_without_click_or_long_click_semantics() {
        composeTestRule.setContent {
            SchoolQuizTheme {
                NoirSettingsScreen(
                    profile = UserProfile.offline(),
                    appVersionName = "0.1.0",
                    appVersionCode = 1,
                    onSyncNow = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        composeTestRule
            .onNodeWithText("v0.1.0 (1)")
            .assert(SemanticsMatcher("no OnClick action") { node ->
                !node.config.contains(SemanticsActions.OnClick)
            })
            .assert(SemanticsMatcher("no OnLongClick action") { node ->
                !node.config.contains(SemanticsActions.OnLongClick)
            })
    }

    // SCH-2: settings_footer_pinned_visible_bottom_bounds
    // GIVEN the settings screen is rendered in the visible viewport
    // WHEN root and footer bounds are measured
    // THEN footer is pinned near the visible bottom and not rendered directly after the short list.
    @Test
    fun settings_footer_is_pinned_to_visible_bottom_not_short_list_tail() {
        composeTestRule.setContent {
            SchoolQuizTheme {
                NoirSettingsScreen(
                    profile = UserProfile.offline(),
                    appVersionName = "0.1.0",
                    appVersionCode = 1,
                    onSyncNow = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        val rootBounds = composeTestRule.onRoot().getUnclippedBoundsInRoot()
        val footerBounds =
            composeTestRule
                .onNodeWithText("v0.1.0 (1)")
                .assertIsDisplayed()
                .getUnclippedBoundsInRoot()

        assertTrue((rootBounds.bottom - footerBounds.bottom).value <= 32f)
        assertTrue((footerBounds.top - rootBounds.top).value > rootBounds.height.value * 0.70f)
    }

    // Spec: hamburger_click_sends_open_drawer_to_domain (cross-phase review fix)
    // Fix: hamburger onClick calls navigator.goTo(Destination.OpenDrawer) directly instead of
    // drawerState.open() + snapshotFlow (avoids !state.isDrawerOpen condition race).
    // GIVEN AppShellScreen rendered on non-SHOP tab
    // WHEN user clicks the hamburger (Menu) icon
    // THEN domain state isDrawerOpen == true unconditionally
    @Test
    fun hamburger_click_sends_open_drawer_to_domain() {
        val lifecycle = LifecycleRegistry()
        lifecycle.resume()
        val component = createRootComponent(lifecycle)

        composeTestRule.setContent {
            SchoolQuizTheme {
                AppShellScreen(
                    rootComponent = component,
                    appVersionName = "test",
                    appVersionCode = 1,
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Open menu").performClick()
        composeTestRule.waitForIdle()

        assertTrue(runBlocking { component.appShellState.first() }.isDrawerOpen)

        lifecycle.stop()
        lifecycle.destroy()
    }

    // SCH-2: drawer_version_behavior_unchanged_guard
    // GIVEN drawer footer behavior predates SCH-2
    // WHEN AppShellScreen receives both settings version fields and the drawer is opened
    // THEN drawer footer keeps the existing name-only version label.
    @Test
    fun drawer_footer_version_label_remains_name_only_after_settings_version_code_wiring() {
        val lifecycle = LifecycleRegistry()
        lifecycle.resume()
        val component = createRootComponent(lifecycle)

        composeTestRule.setContent {
            SchoolQuizTheme {
                AppShellScreen(
                    rootComponent = component,
                    appVersionName = "test",
                    appVersionCode = 1,
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Open menu").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("vtest").assertIsDisplayed()
        composeTestRule.onNodeWithText("vtest (1)").assertDoesNotExist()

        lifecycle.stop()
        lifecycle.destroy()
    }

    private fun createRootComponent(lifecycle: LifecycleRegistry): DefaultRootComponent {
        val componentCtx = DefaultComponentContext(lifecycle)
        val repo = object : UserStatsRepository {
            private val _stats = MutableStateFlow(UserStats.guest())
            override fun observeStats(): Flow<UserStats> = _stats.asStateFlow()
            override suspend fun currentStats(): UserStats = _stats.value
            override suspend fun setLocalDeveloperLevel(value: Int) = Unit
            override suspend fun refreshProfile(): Result<Unit> = Result.success(Unit)
        }
        return DefaultRootComponent(
            componentContext = componentCtx,
            initUseCase = InitializeAppShellUseCase(repo),
            navigateUseCase = NavigateUseCase(),
            observeUseCase = ObserveAppShellStateUseCase(repo),
            retapUseCase = OnTabRetapUseCase(),
            userStatsRepository = repo,
            syncScheduler = object : SyncScheduler {
                override fun enqueueManualSync() = Unit

                override fun applyFrequency(frequency: SyncFrequency) = Unit
override fun applyProfileFrequency(frequency: SyncFrequency) = Unit
            },
            myQuestsFactory = { _, _, _ ->
                object : MyQuestsComponent {
                    override val state: StateFlow<MyQuestsUiState> = MutableStateFlow(MyQuestsUiState())
                    override fun onCatalogSelected(id: CatalogId?) = Unit
                    override fun onCreateQuestClick() = Unit
                    override fun onQuestClick(quest: QuestDisplayItem) = Unit
                    override fun onDraftClick(draft: DraftQuestDisplayItem) = Unit
                }
            },
            homeQuestsFactory = { _, _ ->
                object : HomeQuestsComponent {
                    override val state: StateFlow<HomeQuestsUiState> = MutableStateFlow(HomeQuestsUiState())
                    override fun onCatalogClick(id: CatalogId, name: String) = Unit
                }
            },
            quizzesFactory = { _ ->
                object : QuizzesComponent {
                    private val idleStack = ChildStack(
                        active = Child.Created(QuizzesConfig.Idle, QuizzesChild.Idle),
                        backStack = emptyList(),
                    )
                    override val childStack = MutableValue(idleStack)
                    override fun openLessonRunner(lessonId: String) = Unit
                    override val currentCatalogName: kotlinx.coroutines.flow.StateFlow<String?> =
                        kotlinx.coroutines.flow.MutableStateFlow(null)
                    override val currentCatalogIcons: kotlinx.coroutines.flow.StateFlow<List<androidx.compose.ui.graphics.vector.ImageVector>> =
                        kotlinx.coroutines.flow.MutableStateFlow(emptyList<androidx.compose.ui.graphics.vector.ImageVector>())
                    override fun openQuestList(catalogId: CatalogId, catalogName: String) = Unit
                    override fun openCourseArchive() = Unit
                    override fun openCourseArena() = Unit
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
    }
}
