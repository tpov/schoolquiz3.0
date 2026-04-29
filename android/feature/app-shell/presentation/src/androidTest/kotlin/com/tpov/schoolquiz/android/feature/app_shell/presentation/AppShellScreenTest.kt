package com.tpov.schoolquiz.android.feature.app_shell.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.lifecycle.stop
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
import com.tpov.schoolquiz.android.feature.app_shell.presentation.component.DefaultRootComponent
import com.tpov.schoolquiz.android.feature.app_shell.presentation.ui.AppShellScreen
import com.tpov.schoolquiz.android.feature.app_shell.presentation.ui.UnderConstructionScreen
import com.tpov.schoolquiz.android.feature.app_shell.presentation.ui.scroll.LocalScrollToTopRegistry
import com.tpov.schoolquiz.android.feature.app_shell.presentation.ui.scroll.ScrollToTopRegistry
import com.arkivanov.decompose.Child
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.MutableValue
import com.tpov.schoolquiz.android.core.designsystem.model.QuestDisplayItem
import com.tpov.schoolquiz.android.feature.quest.presentation.HomeQuestsComponent
import com.tpov.schoolquiz.android.feature.quest.presentation.HomeQuestsUiState
import com.tpov.schoolquiz.android.feature.quest.presentation.MyQuestsComponent
import com.tpov.schoolquiz.android.feature.quest.presentation.MyQuestsUiState
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesChild
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesComponent
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.UserStats
import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.UserStatsRepository
import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.InitializeAppShellUseCase
import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.NavigateUseCase
import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.ObserveAppShellStateUseCase
import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.OnTabRetapUseCase
import androidx.work.WorkManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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
        composeTestRule.onNodeWithText("Скоро здесь будет...").assertIsDisplayed()
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
        val componentCtx = DefaultComponentContext(lifecycle)
        val repo = object : UserStatsRepository {
            private val _stats = MutableStateFlow(UserStats.guest())
            override fun observeStats(): Flow<UserStats> = _stats.asStateFlow()
            override suspend fun currentStats(): UserStats = _stats.value
            override suspend fun setLocalDeveloperLevel(value: Int) = Unit
            override suspend fun refreshProfile(): Result<Unit> = Result.success(Unit)
        }
        val component = DefaultRootComponent(
            componentContext = componentCtx,
            initUseCase = InitializeAppShellUseCase(repo),
            navigateUseCase = NavigateUseCase(),
            observeUseCase = ObserveAppShellStateUseCase(repo),
            retapUseCase = OnTabRetapUseCase(),
            userStatsRepository = repo,
            workManager = mock(WorkManager::class.java),
            myQuestsFactory = { _, _, _ ->
                object : MyQuestsComponent {
                    override val state: StateFlow<MyQuestsUiState> = MutableStateFlow(MyQuestsUiState())
                    override fun onCatalogSelected(id: CatalogId?) = Unit
                    override fun onCreateQuestClick() = Unit
                    override fun onQuestClick(quest: QuestDisplayItem) = Unit
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
                    override val currentCatalogName: kotlinx.coroutines.flow.StateFlow<String?> =
                        kotlinx.coroutines.flow.MutableStateFlow(null)
                    override val currentCatalogIcons: kotlinx.coroutines.flow.StateFlow<List<androidx.compose.ui.graphics.vector.ImageVector>> =
                        kotlinx.coroutines.flow.MutableStateFlow(emptyList<androidx.compose.ui.graphics.vector.ImageVector>())
                    override fun openQuestList(catalogId: CatalogId, catalogName: String) = Unit
                    override fun openSectionList(questId: QuestId, titles: List<String>) = Unit
                    override fun dismissQuizzes() = Unit
                    override fun popToLevel(uiLevel: Int) = Unit
                    override fun popCurrentChild() = Unit
                }
            },
        )

        composeTestRule.setContent {
            SchoolQuizTheme {
                AppShellScreen(
                    rootComponent = component,
                    appVersionName = "test",
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
}
