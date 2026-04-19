package com.tpov.schoolquiz.android.feature.app_shell.presentation.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.fade
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation
import com.tpov.schoolquiz.android.core.designsystem.catalog.DesignCatalogScreen
import com.tpov.schoolquiz.android.feature.app_shell.presentation.component.DefaultRootComponent
import com.tpov.schoolquiz.android.feature.app_shell.presentation.screen.EventsScreenComponent
import com.tpov.schoolquiz.android.feature.app_shell.presentation.screen.InternetScreenComponent
import com.tpov.schoolquiz.android.feature.app_shell.presentation.screen.LocalScreenComponent
import com.tpov.schoolquiz.android.feature.app_shell.presentation.screen.ShopScreenComponent
import com.tpov.schoolquiz.android.feature.app_shell.presentation.ui.drawer.DrawerContent
import com.tpov.schoolquiz.android.feature.app_shell.presentation.ui.labels.displayName
import com.tpov.schoolquiz.android.feature.app_shell.presentation.ui.labels.icon
import com.tpov.schoolquiz.android.feature.app_shell.presentation.ui.scroll.LocalScrollToTopRegistry
import com.tpov.schoolquiz.android.feature.app_shell.presentation.ui.scroll.ScrollToTopRegistry
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.BadgeContent
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Destination
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.LocalConfig
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.RetapOutcome
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Tab
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.UserStats
import com.tpov.schoolquiz.shared.feature.app_shell.domain.state.AppShellState
import kotlinx.coroutines.launch

private const val TAB_CROSSFADE_DURATION_MS = 300

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
 * @param isDebugBuild Passed from app layer (BuildConfig.DEBUG). Controls DesignCatalogScreen
 *        visibility (AC 8). Library modules cannot access BuildConfig directly.
 */
@Suppress(
    "FunctionNaming",
    "ktlint:standard:function-naming",
    "LongMethod",
    "UnusedParameter",
)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppShellScreen(
    rootComponent: DefaultRootComponent,
    appVersionName: String,
    isDebugBuild: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val state by rootComponent.appShellState.collectAsStateWithLifecycle(
        initialValue = AppShellState.fallback(UserStats.guest()),
    )
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    val registry = remember { ScrollToTopRegistry() }

    // rememberUpdatedState ensures the collector always reads the current state, not the one
    // captured at LaunchedEffect launch time (stale-closure fix for LaunchedEffect(drawerState)).
    val currentState by rememberUpdatedState(state)

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

    CompositionLocalProvider(LocalScrollToTopRegistry provides registry) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = !state.isShopActive,
            drawerContent = {
                DrawerContent(
                    userStats = state.userStats,
                    activeTab = state.activeTab,
                    activeSection = state.activeSection,
                    navigator = rootComponent.navigator,
                    isDebugBuild = isDebugBuild,
                    versionName = appVersionName,
                )
            },
            modifier = modifier,
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(state.activeSection?.displayName ?: state.activeTab.displayName)
                        },
                        navigationIcon = {
                            if (!state.isShopActive) {
                                IconButton(onClick = { rootComponent.navigator.goTo(Destination.OpenDrawer) }) {
                                    Icon(Icons.Default.Menu, contentDescription = "Open menu")
                                }
                            }
                        },
                    )
                },
                bottomBar = {
                    NavigationBar {
                        Tab.entries.forEach { tab ->
                            BrandNavBarItem(
                                tab = tab,
                                selected = state.activeTab == tab,
                                badge = null,
                                onClick = {
                                    if (tab == state.activeTab) {
                                        val outcome = rootComponent.onActiveTabRetap(tab)
                                        if (outcome == RetapOutcome.NO_OP) {
                                            coroutineScope.launch {
                                                registry.current(tab)?.scrollToTop()
                                            }
                                        }
                                    } else {
                                        rootComponent.navigator.goTo(Destination.SwitchTab(tab))
                                    }
                                },
                            )
                        }
                    }
                },
            ) { paddingValues ->
                AppShellContent(rootComponent, state.activeTab, paddingValues, isDebugBuild)
            }
        }
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun AppShellContent(
    rootComponent: DefaultRootComponent,
    activeTab: Tab,
    paddingValues: PaddingValues,
    isDebugBuild: Boolean,
) {
    Crossfade(
        targetState = activeTab,
        animationSpec = tween(TAB_CROSSFADE_DURATION_MS),
        label = "tab_crossfade",
    ) { tab ->
        when (tab) {
            Tab.LOCAL ->
                Children(
                    rootComponent.localTabComponent.childStack,
                    animation = stackAnimation(fade(tween(TAB_CROSSFADE_DURATION_MS))),
                ) { child ->
                    LocalTabContent(child.instance, paddingValues, isDebugBuild)
                }
            Tab.INTERNET ->
                Children(
                    rootComponent.internetTabComponent.childStack,
                    animation = stackAnimation(fade(tween(TAB_CROSSFADE_DURATION_MS))),
                ) { child ->
                    InternetTabContent(child.instance, paddingValues)
                }
            Tab.EVENTS ->
                Children(
                    rootComponent.eventsTabComponent.childStack,
                    animation = stackAnimation(fade(tween(TAB_CROSSFADE_DURATION_MS))),
                ) { child ->
                    EventsTabContent(child.instance, paddingValues)
                }
            Tab.SHOP ->
                Children(
                    rootComponent.shopTabComponent.childStack,
                    animation = stackAnimation(fade(tween(TAB_CROSSFADE_DURATION_MS))),
                ) { child ->
                    ShopTabContent(child.instance, paddingValues)
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
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(tab.icon, contentDescription = tab.displayName) },
        label = { Text(tab.displayName) },
        modifier = modifier,
    )
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun LocalTabContent(
    screen: LocalScreenComponent,
    paddingValues: PaddingValues,
    isDebugBuild: Boolean,
) {
    when (screen) {
        is LocalScreenComponent.Placeholder -> {
            if (screen.config == LocalConfig.DesignCatalogRoot && isDebugBuild) {
                DesignCatalogScreen(modifier = Modifier.padding(paddingValues))
            } else {
                // AC 8: DesignCatalogRoot shows "Недоступно" in release (not its displayName).
                // TODO: move "Недоступно" to strings.xml (app_shell_unavailable).
                val title =
                    if (screen.config == LocalConfig.DesignCatalogRoot) "Недоступно" else screen.config.displayName
                UnderConstructionScreen(title, modifier = Modifier.padding(paddingValues))
            }
        }
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun InternetTabContent(
    screen: InternetScreenComponent,
    paddingValues: PaddingValues,
) {
    when (screen) {
        is InternetScreenComponent.Placeholder ->
            UnderConstructionScreen(screen.config.displayName, modifier = Modifier.padding(paddingValues))
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun EventsTabContent(
    screen: EventsScreenComponent,
    paddingValues: PaddingValues,
) {
    when (screen) {
        is EventsScreenComponent.Placeholder ->
            UnderConstructionScreen(screen.config.displayName, modifier = Modifier.padding(paddingValues))
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun ShopTabContent(
    screen: ShopScreenComponent,
    paddingValues: PaddingValues,
) {
    when (screen) {
        is ShopScreenComponent.Placeholder ->
            UnderConstructionScreen(screen.config.displayName, modifier = Modifier.padding(paddingValues))
    }
}
