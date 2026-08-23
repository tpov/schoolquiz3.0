package com.tpov.schoolquiz.android.feature.app_shell.presentation.ui

import android.app.Activity
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.fade
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.tpov.schoolquiz.android.core.designsystem.catalog.DesignCatalogScreen
import com.tpov.schoolquiz.android.core.designsystem.components.SchoolQuizDesignBackground
import com.tpov.schoolquiz.android.core.designsystem.components.SchoolQuizDesignStyle
import com.tpov.schoolquiz.android.core.designsystem.currentSchoolQuizDesignStyle
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirAppBar
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirBottomNav
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirIconButton
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirIcons
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirNavItem
import com.tpov.schoolquiz.android.feature.app_shell.presentation.component.DefaultRootComponent
import com.tpov.schoolquiz.android.feature.app_shell.presentation.component.TournamentOverviewLoadState
import com.tpov.schoolquiz.android.feature.app_shell.presentation.screen.EventsScreenComponent
import com.tpov.schoolquiz.android.feature.app_shell.presentation.screen.InternetScreenComponent
import com.tpov.schoolquiz.android.feature.app_shell.presentation.screen.LocalScreenComponent
import com.tpov.schoolquiz.android.feature.app_shell.presentation.screen.ShopScreenComponent
import com.tpov.schoolquiz.android.feature.app_shell.presentation.ui.drawer.DrawerContent
import com.tpov.schoolquiz.android.feature.app_shell.presentation.ui.labels.displayName
import com.tpov.schoolquiz.android.feature.app_shell.presentation.ui.labels.icon
import com.tpov.schoolquiz.android.feature.app_shell.presentation.ui.scroll.LocalScrollToTopRegistry
import com.tpov.schoolquiz.android.feature.app_shell.presentation.ui.scroll.ScrollToTopRegistry
import com.tpov.schoolquiz.android.feature.economy.presentation.screen.ShopScreen
import com.tpov.schoolquiz.android.feature.internet.profile.presentation.screen.ProfileScreen
import com.tpov.schoolquiz.android.feature.local.settings.presentation.ui.DesignSettingsScreen
import com.tpov.schoolquiz.android.feature.quest.presentation.ui.HomeQuestsScreen
import com.tpov.schoolquiz.android.feature.quest.presentation.ui.MyQuestsScreen
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.screen.QuestCreateScreen
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.screen.ReviewQueueScreen
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuizzesChild
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.screen.QuizzesScreen
import com.tpov.schoolquiz.shared.core.foundation.QualificationLevel
import com.tpov.schoolquiz.shared.feature.app_shell.domain.logic.visibleFooterActions
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.BadgeContent
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Destination
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.DrawerFooterAction
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.DrawerSection
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.EventsConfig
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.InternetConfig
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.LocalConfig
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.RetapOutcome
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.RootEvent
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Tab
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.UserStats
import com.tpov.schoolquiz.shared.feature.app_shell.domain.state.AppShellState
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val TAB_CROSSFADE_DURATION_MS = 300
private const val MAIN_INDICATOR_ALPHA = 0.14f
private const val CLEAN_INDICATOR_ALPHA = 0.08f
private const val HOME_SHELF = "home"
private const val QUALIFIER_TOURNAMENT_SHELF = "tournament"
private const val WORLD_CHAMPIONSHIP_SHELF = "tournamentFinal"

private data class AppShellUiAccess(
    val canSeeDesignCatalog: Boolean,
    val canManagePublicShelves: Boolean,
)

/**
 * Root shell Composable.
 *
 * Wires: ModalNavigationDrawer + Scaffold + TopAppBar + NavigationBar + per-tab Children.
 * Domain → UI: collectAsStateWithLifecycle() on rootComponent.appShellState.
 * UI → Domain: navigator.goTo() + onActiveTabRetap().
 * Drawer sync: 2 LaunchedEffect (bidirectional UI↔domain state sync).
 *
 * @param appVersionName Version string from app layer (BuildConfig.VERSION_NAME).
 *        Library modules cannot access BuildConfig.VERSION_NAME directly (H8 fix).
 * @param appVersionCode Version code from app layer (BuildConfig.VERSION_CODE).
 * @param isDebugBuild Passed from app layer (BuildConfig.DEBUG). Controls DesignCatalogScreen
 *        visibility (AC 8). Library modules cannot access BuildConfig directly.
 */
@Suppress(
    "FunctionNaming",
    "ktlint:standard:function-naming",
    "LongMethod",
    "CyclomaticComplexMethod",
    "UnusedParameter",
)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppShellScreen(
    rootComponent: DefaultRootComponent,
    appVersionName: String,
    appVersionCode: Int,
    isDebugBuild: Boolean = false,
    selectedDesignStyle: SchoolQuizDesignStyle = SchoolQuizDesignStyle.Main,
    onDesignStyleSelected: (SchoolQuizDesignStyle) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state by rootComponent.appShellState.collectAsStateWithLifecycle(
        initialValue = AppShellState.fallback(UserStats.guest()),
    )
    val tournamentOverviewState by rootComponent.tournamentOverviewState.collectAsStateWithLifecycle(
        initialValue = emptyMap(),
    )
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    val registry = remember { ScrollToTopRegistry() }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // rememberUpdatedState ensures the collector always reads the current state, not the one
    // captured at LaunchedEffect launch time (stale-closure fix for LaunchedEffect(drawerState)).
    val currentState by rememberUpdatedState(state)
    val quizzesStack by rootComponent.quizzesComponent.childStack.subscribeAsState()
    val isRunnerActive = quizzesStack.active.instance is QuizzesChild.LessonRunner
    val questCreateState by rootComponent.questCreateComponent.state.collectAsStateWithLifecycle(
        initialValue = rootComponent.questCreateComponent.state.value,
    )
    val isQuestAuthoringEditorActive =
        state.activeTab == Tab.LOCAL &&
            state.localState.stack.active == LocalConfig.QuestCreateRoot &&
            questCreateState.editor != null
    val isImmersiveScreenActive = isRunnerActive || isQuestAuthoringEditorActive

    // Drawer sync: UI drawer state → domain (journeys 5, 7, 8).
    // onOpenDrawer/onCloseDrawer are idempotent (AppShellTransitions): repeated calls produce equal
    // state, MutableStateFlow skips emission — no feedback loop with LaunchedEffect(state.isDrawerOpen).
    LaunchedEffect(drawerState) {
        snapshotFlow { drawerState.currentValue }.collect { currentValue ->
            when {
                currentValue == DrawerValue.Open && !currentState.isDrawerOpen ->
                    rootComponent.navigator.goTo(Destination.OpenDrawer)
                currentValue == DrawerValue.Closed && currentState.isDrawerOpen ->
                    rootComponent.navigator.goTo(Destination.CloseDrawer)
            }
        }
    }

    // Drawer sync: domain → UI drawer state
    LaunchedEffect(state.isDrawerOpen) {
        if (state.isDrawerOpen) drawerState.open() else drawerState.close()
    }

    // Event collector: object-key prevents duplicate collectors on recomposition.
    LaunchedEffect(rootComponent) {
        rootComponent.events.collect { event ->
            when (event) {
                RootEvent.DevModeActivated ->
                    snackbarHostState.showSnackbar(
                        message = "Режим разработчика включён",
                        duration = SnackbarDuration.Long,
                    )
                RootEvent.DevModeAlreadyActive ->
                    snackbarHostState.showSnackbar(
                        message = "Уже в режиме разработчика",
                        duration = SnackbarDuration.Short,
                    )
                RootEvent.SyncStarted ->
                    snackbarHostState.showSnackbar(
                        message = "Синхронизация запущена",
                        duration = SnackbarDuration.Short,
                    )
                RootEvent.SystemBack -> (context as? Activity)?.moveTaskToBack(true)
            }
        }
    }

    val canSeeDesignCatalog =
        visibleFooterActions(isDebugBuild, state.userStats)
            .contains(DrawerFooterAction.DesignCatalog)
    val canManagePublicShelves = state.userStats.qualification.developer > QualificationLevel.LEVEL_1.points
    val uiAccess =
        AppShellUiAccess(
            canSeeDesignCatalog = canSeeDesignCatalog,
            canManagePublicShelves = canManagePublicShelves,
        )

    CompositionLocalProvider(LocalScrollToTopRegistry provides registry) {
        ModalNavigationDrawer(
            modifier = modifier,
            drawerState = drawerState,
            gesturesEnabled = !state.isShopActive && !isImmersiveScreenActive,
            drawerContent = {
                DrawerContent(
                    userStats = state.userStats,
                    activeTab = state.activeTab,
                    activeSection = state.activeSection,
                    navigator = rootComponent.navigator,
                    isDebugBuild = isDebugBuild,
                    versionName = appVersionName,
                    onVersionTap = { rootComponent.onVersionTap(System.currentTimeMillis()) },
                    onSyncNow = { rootComponent.onSyncNow() },
                    onDismissQuizzes = { rootComponent.quizzesComponent.dismissQuizzes() },
                )
            },
        ) {
            Scaffold(
                topBar = {
                    if (!isImmersiveScreenActive) {
                        NoirAppBar(
                            title = state.activeSection?.displayName ?: state.activeTab.displayName,
                            leading =
                                if (state.isShopActive) {
                                    null
                                } else {
                                    {
                                        NoirIconButton(
                                            icon = NoirIcons.Sliders,
                                            contentDescription = "Open menu",
                                            onClick = {
                                                rootComponent.navigator.goTo(Destination.OpenDrawer)
                                            },
                                        )
                                    }
                                },
                        )
                    }
                },
                bottomBar = {
                    if (!isImmersiveScreenActive) {
                        NoirBottomNav(
                            items = Tab.entries.map { NoirNavItem(it.displayName, it.noirIcon) },
                            selectedIndex = Tab.entries.indexOf(state.activeTab),
                            onSelect = { index ->
                                val tab = Tab.entries[index]
                                if (tab == state.activeTab) {
                                    val outcome = rootComponent.onActiveTabRetap(tab)
                                    if (outcome == RetapOutcome.NO_OP) {
                                        coroutineScope.launch {
                                            registry.current(tab)?.scrollToTop()
                                        }
                                    }
                                } else {
                                    rootComponent.quizzesComponent.dismissQuizzes()
                                    rootComponent.navigator.goTo(Destination.SwitchTab(tab))
                                }
                            },
                        )
                    }
                },
                snackbarHost = { SnackbarHost(snackbarHostState) },
                containerColor = MaterialTheme.colorScheme.background,
            ) { paddingValues ->
                SchoolQuizDesignBackground(
                    isHard = false,
                    accentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    AppShellContent(
                        rootComponent = rootComponent,
                        state = state,
                        appVersionName = appVersionName,
                        appVersionCode = appVersionCode,
                        paddingValues = paddingValues,
                        uiAccess = uiAccess,
                        tournamentOverviewState = tournamentOverviewState,
                        selectedDesignStyle = selectedDesignStyle,
                        onDesignStyleSelected = onDesignStyleSelected,
                    )
                    if (quizzesStack.active.instance !is QuizzesChild.Idle) {
                        val overlayModifier =
                            if (isRunnerActive) {
                                Modifier.fillMaxSize()
                            } else {
                                Modifier.fillMaxSize().padding(paddingValues)
                            }
                        Box(modifier = overlayModifier) {
                            QuizzesScreen(
                                component = rootComponent.quizzesComponent,
                                canManagePublicShelves = uiAccess.canManagePublicShelves,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun AppShellContent(
    rootComponent: DefaultRootComponent,
    state: AppShellState,
    appVersionName: String,
    appVersionCode: Int,
    paddingValues: PaddingValues,
    uiAccess: AppShellUiAccess,
    tournamentOverviewState: Map<String, TournamentOverviewLoadState>,
    selectedDesignStyle: SchoolQuizDesignStyle,
    onDesignStyleSelected: (SchoolQuizDesignStyle) -> Unit,
) {
    Crossfade(
        targetState = state.activeTab,
        animationSpec = tween(TAB_CROSSFADE_DURATION_MS),
        label = "tab_crossfade",
    ) { tab ->
        when (tab) {
            Tab.LOCAL ->
                Children(
                    rootComponent.localTabComponent.childStack,
                    animation = stackAnimation(fade(tween(TAB_CROSSFADE_DURATION_MS))),
                ) { child ->
                    LocalTabContent(
                        rootComponent = rootComponent,
                        screen = child.instance,
                        appVersionName = appVersionName,
                        appVersionCode = appVersionCode,
                        paddingValues = paddingValues,
                        canSeeDesignCatalog = uiAccess.canSeeDesignCatalog,
                        canManagePublicShelves = uiAccess.canManagePublicShelves,
                        selectedDesignStyle = selectedDesignStyle,
                        onDesignStyleSelected = onDesignStyleSelected,
                    )
                }
            Tab.INTERNET ->
                Children(
                    rootComponent.internetTabComponent.childStack,
                    animation = stackAnimation(fade(tween(TAB_CROSSFADE_DURATION_MS))),
                ) { child ->
                    InternetTabContent(rootComponent, child.instance, paddingValues)
                }
            Tab.EVENTS ->
                Children(
                    rootComponent.eventsTabComponent.childStack,
                    animation = stackAnimation(fade(tween(TAB_CROSSFADE_DURATION_MS))),
                ) { child ->
                    EventsTabContent(
                        rootComponent = rootComponent,
                        screen = child.instance,
                        paddingValues = paddingValues,
                        canManagePublicShelves = uiAccess.canManagePublicShelves,
                        userStats = state.userStats,
                        tournamentOverviewState = tournamentOverviewState,
                    )
                }
            Tab.SHOP ->
                Children(
                    rootComponent.shopTabComponent.childStack,
                    animation = stackAnimation(fade(tween(TAB_CROSSFADE_DURATION_MS))),
                ) { child ->
                    ShopTabContent(rootComponent, child.instance, paddingValues)
                }
        }
    }
}

/**
 * NavBar item wrapper with nullable badge surface (AC 20).
 * MVP: badge always null (spec BR #15). RowScope extension needed by NavigationBarItem.
 */
@Suppress(
    "FunctionNaming",
    "ktlint:standard:function-naming",
    "UnusedParameter",
)
@Composable
private fun RowScope.BrandNavBarItem(
    tab: Tab,
    selected: Boolean,
    onClick: () -> Unit,
    badge: BadgeContent? = null,
    modifier: Modifier = Modifier,
) {
    val designStyle = currentSchoolQuizDesignStyle()
    val indicatorAlpha =
        when (designStyle) {
            SchoolQuizDesignStyle.Main -> MAIN_INDICATOR_ALPHA
            SchoolQuizDesignStyle.Clean -> CLEAN_INDICATOR_ALPHA
        }
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(tab.icon, contentDescription = tab.displayName) },
        label = { Text(tab.displayName) },
        modifier = modifier,
        colors =
            NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = indicatorAlpha),
            ),
    )
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun LocalTabContent(
    rootComponent: DefaultRootComponent,
    screen: LocalScreenComponent,
    appVersionName: String,
    appVersionCode: Int,
    paddingValues: PaddingValues,
    canSeeDesignCatalog: Boolean,
    canManagePublicShelves: Boolean,
    selectedDesignStyle: SchoolQuizDesignStyle,
    onDesignStyleSelected: (SchoolQuizDesignStyle) -> Unit,
) {
    when (screen) {
        is LocalScreenComponent.Placeholder -> {
            when (screen.config) {
                is LocalConfig.DesignCatalogRoot ->
                    if (canSeeDesignCatalog) {
                        DesignCatalogScreen(modifier = Modifier.padding(paddingValues))
                    } else {
                        // AC 8: DesignCatalogRoot shows "Недоступно" in release.
                        UnderConstructionScreen("Недоступно", modifier = Modifier.padding(paddingValues))
                    }
                is LocalConfig.MyQuestsRoot ->
                    MyQuestsContent(rootComponent = rootComponent, paddingValues = paddingValues)
                is LocalConfig.HomeQuestsRoot ->
                    HomeQuestsContent(
                        rootComponent = rootComponent,
                        paddingValues = paddingValues,
                        canManagePublicShelves = canManagePublicShelves,
                    )
                is LocalConfig.ArchiveRoot ->
                    CourseArchiveContent(rootComponent = rootComponent, paddingValues = paddingValues)
                is LocalConfig.ReviewQueueRoot ->
                    ReviewQueueScreen(
                        component = rootComponent.reviewQueueComponent,
                        modifier = Modifier.padding(paddingValues),
                    )
                is LocalConfig.QuestCreateRoot ->
                    QuestCreateScreen(
                        component = rootComponent.questCreateComponent,
                        modifier = Modifier.padding(paddingValues),
                    )
                is LocalConfig.SettingsRoot ->
                    DesignSettingsScreen(
                        selectedStyle = selectedDesignStyle,
                        onStyleSelected = onDesignStyleSelected,
                        appVersionName = appVersionName,
                        appVersionCode = appVersionCode,
                        modifier = Modifier.padding(paddingValues),
                    )
                is LocalConfig.EmptyRoot ->
                    UnderConstructionScreen(screen.config.displayName, modifier = Modifier.padding(paddingValues))
            }
        }
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun CourseArchiveContent(
    rootComponent: DefaultRootComponent,
    paddingValues: PaddingValues,
) {
    LaunchedEffect(rootComponent.quizzesComponent) {
        rootComponent.quizzesComponent.openCourseArchive()
    }
    Box(modifier = Modifier.padding(paddingValues).fillMaxSize())
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun MyQuestsContent(
    rootComponent: DefaultRootComponent,
    paddingValues: PaddingValues,
) {
    MyQuestsScreen(component = rootComponent.myQuestsComponent, modifier = Modifier.padding(paddingValues))
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun HomeQuestsContent(
    rootComponent: DefaultRootComponent,
    paddingValues: PaddingValues,
    canManagePublicShelves: Boolean,
) {
    HomeQuestsScreen(
        component = rootComponent.homeQuestsComponent,
        modifier = Modifier.padding(paddingValues),
        canManagePublicShelves = canManagePublicShelves,
        onAddPublicQuestClick = {
            rootComponent.quizzesComponent.openPublicQuestCatalogPicker(
                targetShelf = HOME_SHELF,
                title = "Домашние квесты",
            )
        },
    )
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun InternetTabContent(
    rootComponent: DefaultRootComponent,
    screen: InternetScreenComponent,
    paddingValues: PaddingValues,
) {
    when (screen) {
        is InternetScreenComponent.Placeholder ->
            when (screen.config) {
                InternetConfig.ProfileRoot ->
                    ProfileScreen(
                        component = rootComponent.profileComponent,
                        modifier = Modifier.padding(paddingValues),
                    )
                InternetConfig.ArenaRoot ->
                    CourseArenaContent(rootComponent = rootComponent, paddingValues = paddingValues)
                else ->
                    UnderConstructionScreen(screen.config.displayName, modifier = Modifier.padding(paddingValues))
            }
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun CourseArenaContent(
    rootComponent: DefaultRootComponent,
    paddingValues: PaddingValues,
) {
    LaunchedEffect(rootComponent.quizzesComponent) {
        rootComponent.quizzesComponent.openCourseArena()
    }
    Box(modifier = Modifier.padding(paddingValues).fillMaxSize())
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun EventsTabContent(
    rootComponent: DefaultRootComponent,
    screen: EventsScreenComponent,
    paddingValues: PaddingValues,
    canManagePublicShelves: Boolean,
    userStats: UserStats,
    tournamentOverviewState: Map<String, TournamentOverviewLoadState>,
) {
    when (screen) {
        is EventsScreenComponent.Placeholder ->
            when (screen.config) {
                EventsConfig.QualifierTournamentRoot ->
                    TournamentEventContent(
                        rootComponent = rootComponent,
                        paddingValues = paddingValues,
                        canManagePublicShelves = canManagePublicShelves,
                        targetShelf = QUALIFIER_TOURNAMENT_SHELF,
                        title = "Отборочный турнир",
                        stageLabel = "Лёгкие вопросы",
                        forcedHardMode = false,
                    )
                EventsConfig.QualifierTournamentLeaderboardRoot ->
                    TournamentLeaderboardScreen(
                        model =
                            qualifierLeaderboardModel(
                                userStats = userStats,
                                loadState = tournamentOverviewState[QUALIFIER_TOURNAMENT_SHELF],
                            ),
                        modifier = Modifier.padding(paddingValues),
                    )
                EventsConfig.QualifierTournamentParticipantsRoot ->
                    TournamentParticipantsScreen(
                        model =
                            qualifierParticipantsModel(
                                userStats = userStats,
                                loadState = tournamentOverviewState[QUALIFIER_TOURNAMENT_SHELF],
                            ),
                        modifier = Modifier.padding(paddingValues),
                    )
                EventsConfig.WorldChampionshipRoot ->
                    TournamentEventContent(
                        rootComponent = rootComponent,
                        paddingValues = paddingValues,
                        canManagePublicShelves = canManagePublicShelves,
                        targetShelf = WORLD_CHAMPIONSHIP_SHELF,
                        title = "Чемпионат мира",
                        stageLabel = "Сложные вопросы",
                        forcedHardMode = true,
                    )
                EventsConfig.WorldChampionshipLeaderboardRoot ->
                    TournamentLeaderboardScreen(
                        model =
                            worldLeaderboardModel(
                                userStats = userStats,
                                loadState = tournamentOverviewState[WORLD_CHAMPIONSHIP_SHELF],
                            ),
                        modifier = Modifier.padding(paddingValues),
                    )
                EventsConfig.WorldChampionshipParticipantsRoot ->
                    TournamentParticipantsScreen(
                        model =
                            worldParticipantsModel(
                                userStats = userStats,
                                loadState = tournamentOverviewState[WORLD_CHAMPIONSHIP_SHELF],
                            ),
                        modifier = Modifier.padding(paddingValues),
                    )
                else ->
                    UnderConstructionScreen(screen.config.displayName, modifier = Modifier.padding(paddingValues))
            }
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun TournamentEventContent(
    rootComponent: DefaultRootComponent,
    paddingValues: PaddingValues,
    canManagePublicShelves: Boolean,
    targetShelf: String,
    title: String,
    stageLabel: String,
    forcedHardMode: Boolean,
) {
    val eventSection =
        if (targetShelf == QUALIFIER_TOURNAMENT_SHELF) {
            DrawerSection.EventsSection.QualifierTournament
        } else {
            DrawerSection.EventsSection.WorldChampionship
        }
    val openTournamentLessons = {
        rootComponent.quizzesComponent.openPublicQuestShelfCatalog(
            targetShelf = targetShelf,
            title = title,
            forcedHardMode = forcedHardMode,
        )
    }
    TournamentEventScreen(
        model =
            TournamentEventUi(
                title = title,
                stageLabel = stageLabel,
            ),
        actions =
            TournamentEventActions(
                canManagePublicShelves = canManagePublicShelves,
                onStartClick = openTournamentLessons,
                onLeaderboardClick = {
                    rootComponent.navigator.goTo(Destination.OpenTournamentLeaderboard(eventSection))
                },
                onLessonsClick = openTournamentLessons,
                onParticipantsClick = {
                    rootComponent.navigator.goTo(Destination.OpenTournamentParticipants(eventSection))
                },
                onAddLessonsClick = {
                    rootComponent.quizzesComponent.openPublicQuestCatalogPicker(
                        targetShelf = targetShelf,
                        title = title,
                    )
                },
            ),
        modifier = Modifier.padding(paddingValues),
    )
}

private fun qualifierLeaderboardModel(
    userStats: UserStats,
    loadState: TournamentOverviewLoadState?,
): TournamentLeaderboardUi =
    tournamentLeaderboardModel(
        title = "Отборочный турнир",
        stageLabel = "Лёгкие вопросы",
        qualificationRule = "Топ-32",
        userStats = userStats,
        loadState = loadState,
    )

private fun worldLeaderboardModel(
    userStats: UserStats,
    loadState: TournamentOverviewLoadState?,
): TournamentLeaderboardUi =
    tournamentLeaderboardModel(
        title = "Чемпионат мира",
        stageLabel = "Сложные вопросы",
        qualificationRule = "Финальная таблица",
        userStats = userStats,
        loadState = loadState,
    )

private fun tournamentLeaderboardModel(
    title: String,
    stageLabel: String,
    qualificationRule: String,
    userStats: UserStats,
    loadState: TournamentOverviewLoadState?,
): TournamentLeaderboardUi {
    val overview = loadState?.overview
    val participantsByUser = overview?.participants.orEmpty().associateBy { it.userId }
    return TournamentLeaderboardUi(
        title = title,
        stageLabel = stageLabel,
        qualificationRule = qualificationRule,
        currentUserNickname = userStats.nickname,
        currentUserPercent =
            overview?.currentUserEntry?.averagePercent?.roundToInt()
                ?: overview?.currentUserParticipant?.lastPercent,
        standings =
            overview?.leaderboard.orEmpty().map { standing ->
                TournamentStandingUi(
                    nickname = standing.nickname,
                    percent = standing.averagePercent.roundToInt(),
                    attempts = participantsByUser[standing.userId]?.attemptCount ?: standing.groupsPlayed,
                )
            },
        isLoading = loadState is TournamentOverviewLoadState.Loading,
        errorMessage = (loadState as? TournamentOverviewLoadState.Error)?.message,
    )
}

private fun qualifierParticipantsModel(
    userStats: UserStats,
    loadState: TournamentOverviewLoadState?,
): TournamentParticipantsUi =
    tournamentParticipantsModel(
        title = "Отборочный турнир",
        stageLabel = "Лёгкие вопросы",
        userStats = userStats,
        fallbackStatus = "доступ открыт",
        loadState = loadState,
    )

private fun worldParticipantsModel(
    userStats: UserStats,
    loadState: TournamentOverviewLoadState?,
): TournamentParticipantsUi =
    tournamentParticipantsModel(
        title = "Чемпионат мира",
        stageLabel = "Сложные вопросы",
        userStats = userStats,
        fallbackStatus = "ожидает отбора",
        loadState = loadState,
    )

private fun tournamentParticipantsModel(
    title: String,
    stageLabel: String,
    userStats: UserStats,
    fallbackStatus: String,
    loadState: TournamentOverviewLoadState?,
): TournamentParticipantsUi {
    val overview = loadState?.overview
    val remoteParticipants =
        overview?.participants.orEmpty().map { participant ->
            TournamentParticipantUi(
                nickname = participant.nickname,
                status = participant.status.displayTournamentParticipantStatus(),
                attempts = participant.attemptCount,
            )
        }
    val participants =
        remoteParticipants.ifEmpty {
            listOf(
                TournamentParticipantUi(
                    nickname = userStats.nickname,
                    status = fallbackStatus,
                    attempts = 0,
                ),
            )
        }
    return TournamentParticipantsUi(
        title = title,
        stageLabel = stageLabel,
        participants = participants,
        isLoading = loadState is TournamentOverviewLoadState.Loading,
        errorMessage = (loadState as? TournamentOverviewLoadState.Error)?.message,
    )
}

private fun String.displayTournamentParticipantStatus(): String =
    when (this) {
        "active" -> "участвует"
        else -> this
    }

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun ShopTabContent(
    rootComponent: DefaultRootComponent,
    screen: ShopScreenComponent,
    paddingValues: PaddingValues,
) {
    when (screen) {
        is ShopScreenComponent.Placeholder ->
            ShopScreen(
                component = rootComponent.shopComponent,
                modifier = Modifier.padding(paddingValues),
            )
    }
}

/**
 * Bottom-bar icons, from the NOIR set rather than Material's.
 *
 * Events gets a calendar that had to be drawn for it: the set had a bell and a clock, and both say
 * the wrong thing — one is notifications, the other is time.
 */
private val Tab.noirIcon: androidx.compose.ui.graphics.vector.ImageVector
    get() =
        when (this) {
            Tab.LOCAL -> NoirIcons.Home
            Tab.INTERNET -> NoirIcons.Globe
            Tab.EVENTS -> NoirIcons.Calendar
            Tab.SHOP -> NoirIcons.Bag
        }
