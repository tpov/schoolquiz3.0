---
phase: phase-05
role: frontend-dev
---

# Phase-05: Frontend Tasks — AppShellScreen

## 1. android/core/navigation/build.gradle.kts

```kotlin
plugins {
    id("schoolquiz.android.compose.library")
}

android {
    namespace = "com.tpov.schoolquiz.android.core.navigation"
}

dependencies {
    api(libs.bundles.decompose)
    implementation(libs.bundles.androidx.ui.base)
    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose.ui)
}
```

Note: `android/core/navigation` НЕ зависит от `shared/feature/app-shell/domain` — это pure Decompose helpers без knowledge of feature domain (per grounding Problem 6 constraint).

## 2. Labels.kt — Presentation-layer display name / icon extensions

**Файл**: `android/feature/app-shell/presentation/src/main/kotlin/.../presentation/ui/labels/Labels.kt`

Domain types (`Tab`, `DrawerSection`, `TabConfig`, `DrawerFooterAction`) содержат только business data — display strings в domain нарушали бы domain-purity (локализуемые строки = UI-concern). Все extension properties — в presentation-слое.

```kotlin
package com.tpov.schoolquiz.android.feature.app_shell.presentation.ui.labels

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Stadium
import androidx.compose.ui.graphics.vector.ImageVector
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.DrawerFooterAction
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.DrawerSection
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Tab
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.TabConfig

// ---- Tab ----

val Tab.displayName: String
    get() = when (this) {
        Tab.LOCAL -> "Локальная"
        Tab.INTERNET -> "Интернет"
        Tab.EVENTS -> "События"
        Tab.SHOP -> "Магазин"
    }

val Tab.icon: ImageVector
    get() = when (this) {
        Tab.LOCAL -> Icons.Default.Home
        Tab.INTERNET -> Icons.Default.Language
        Tab.EVENTS -> Icons.Default.Event
        Tab.SHOP -> Icons.Default.ShoppingCart
    }

// ---- DrawerSection ----

val DrawerSection.displayName: String
    get() = when (this) {
        DrawerSection.LocalSection.MyQuests -> "Мои квесты"
        DrawerSection.LocalSection.MyCourses -> "Мои курсы"
        DrawerSection.LocalSection.Settings -> "Настройки"
        DrawerSection.InternetSection.Arena -> "Арена"
        DrawerSection.InternetSection.Catalog -> "Каталог"
        DrawerSection.InternetSection.Qualifications -> "Квалификации"
        DrawerSection.InternetSection.Profile -> "Профиль"
        DrawerSection.InternetSection.Social -> "Социальное"
        DrawerSection.InternetSection.Leaderboard -> "Таблица лидеров"
        DrawerSection.EventsSection.ActiveEvents -> "Активные события"
        DrawerSection.EventsSection.Minigames -> "Мини-игры"
    }

val DrawerSection.icon: ImageVector
    get() = when (this) {
        DrawerSection.LocalSection.MyQuests -> Icons.Default.Book
        DrawerSection.LocalSection.MyCourses -> Icons.Default.Book
        DrawerSection.LocalSection.Settings -> Icons.Default.Settings
        DrawerSection.InternetSection.Arena -> Icons.Default.Stadium
        DrawerSection.InternetSection.Catalog -> Icons.Default.Book
        DrawerSection.InternetSection.Qualifications -> Icons.Default.EmojiEvents
        DrawerSection.InternetSection.Profile -> Icons.Default.AccountCircle
        DrawerSection.InternetSection.Social -> Icons.Default.People
        DrawerSection.InternetSection.Leaderboard -> Icons.Default.Leaderboard
        DrawerSection.EventsSection.ActiveEvents -> Icons.Default.Event
        DrawerSection.EventsSection.Minigames -> Icons.Default.SportsEsports
    }

// ---- TabConfig (used for screen title when activeSection is null) ----

val TabConfig.displayName: String
    get() = when (this) {
        is TabConfig.LocalConfig -> when (this) {
            TabConfig.LocalConfig.MyQuestsRoot -> "Мои квесты"
            TabConfig.LocalConfig.MyCoursesRoot -> "Мои курсы"
            TabConfig.LocalConfig.SettingsRoot -> "Настройки"
            TabConfig.LocalConfig.DesignCatalogRoot -> "Design Catalog"
            TabConfig.LocalConfig.EmptyRoot -> "Локальная"
        }
        is TabConfig.InternetConfig -> when (this) {
            TabConfig.InternetConfig.ArenaRoot -> "Арена"
            TabConfig.InternetConfig.CatalogRoot -> "Каталог"
            TabConfig.InternetConfig.QualificationsRoot -> "Квалификации"
            TabConfig.InternetConfig.ProfileRoot -> "Профиль"
            TabConfig.InternetConfig.SocialRoot -> "Социальное"
            TabConfig.InternetConfig.LeaderboardRoot -> "Таблица лидеров"
            TabConfig.InternetConfig.EmptyRoot -> "Интернет"
        }
        is TabConfig.EventsConfig -> when (this) {
            TabConfig.EventsConfig.ActiveEventsRoot -> "Активные события"
            TabConfig.EventsConfig.MinigamesRoot -> "Мини-игры"
            TabConfig.EventsConfig.EmptyRoot -> "События"
        }
        is TabConfig.ShopConfig -> "Магазин"
    }

// ---- DrawerFooterAction ----

val DrawerFooterAction.displayName: String
    get() = when (this) {
        DrawerFooterAction.DesignCatalog -> "Design Catalog"
        DrawerFooterAction.About -> "О приложении"
    }
```

Note: `TabConfig` sealed hierarchy is `sealed interface TabConfig` with sub-sealed-interfaces. The `when` expression must use smart-cast: `is TabConfig.LocalConfig` then nested `when`. Import `TabConfig.LocalConfig`, `TabConfig.InternetConfig`, `TabConfig.EventsConfig`, `TabConfig.ShopConfig` explicitly if needed.

## 3. ScrollToTopHook.kt

```kotlin
package com.tpov.schoolquiz.android.feature.app_shell.presentation.ui.scroll

/**
 * Interface for screens that can scroll to top.
 * Implemented per-screen by scrollable content.
 * Called by AppShellScreen on RetapOutcome.NO_OP.
 * Spec: 0-spec.md:82. ADR-COMP-06.
 */
interface ScrollToTopHook {
    /** @return true if scroll happened, false if already at top */
    suspend fun scrollToTop(): Boolean
}
```

## 3. ScrollToTopRegistry.kt

```kotlin
package com.tpov.schoolquiz.android.feature.app_shell.presentation.ui.scroll

import androidx.compose.runtime.staticCompositionLocalOf
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Tab

/**
 * Registry mapping Tab → active ScrollToTopHook.
 *
 * Identity-aware unregister: prevents Crossfade overlap from accidentally
 * removing newly registered hook when outgoing screen disposes.
 *
 * Main thread invariant: register/unregister called only from Compose (Main thread).
 * ADR-COMP-06.
 */
class ScrollToTopRegistry {
    private val hooks = mutableMapOf<Tab, ScrollToTopHook>()

    fun register(tab: Tab, hook: ScrollToTopHook) {
        hooks[tab] = hook
    }

    /** Identity check (===): only removes entry if stored instance IS the same reference. */
    fun unregister(tab: Tab, hook: ScrollToTopHook) {
        if (hooks[tab] === hook) hooks.remove(tab)
    }

    fun current(tab: Tab): ScrollToTopHook? = hooks[tab]
}

val LocalScrollToTopRegistry = staticCompositionLocalOf<ScrollToTopRegistry> {
    error("ScrollToTopRegistry not provided — wrap in CompositionLocalProvider")
}
```

## 4. UnderConstructionScreen.kt

```kotlin
package com.tpov.schoolquiz.android.feature.app_shell.presentation.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme

/**
 * Generic placeholder for screens not yet implemented.
 * Spec FR #13: one composable for ~14 sections/placeholders.
 */
@Composable
fun UnderConstructionScreen(
    title: String,
    icon: ImageVector = Icons.Default.Construction,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            // AC 13: subtitle required per spec 0-spec.md:767
            Text(
                text = "Скоро здесь будет...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun UnderConstructionScreenPreview() {
    SchoolQuizTheme {
        UnderConstructionScreen(title = "Мои квесты")
    }
}
```

## 6. AppShellScreen.kt

```kotlin
package com.tpov.schoolquiz.android.feature.app_shell.presentation.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.tpov.schoolquiz.android.feature.app_shell.presentation.component.DefaultRootComponent
import com.tpov.schoolquiz.android.feature.app_shell.presentation.ui.labels.displayName
import com.tpov.schoolquiz.android.feature.app_shell.presentation.ui.labels.icon
import com.tpov.schoolquiz.android.feature.app_shell.presentation.ui.scroll.LocalScrollToTopRegistry
import com.tpov.schoolquiz.android.feature.app_shell.presentation.ui.scroll.ScrollToTopRegistry
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Destination
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.RetapOutcome
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Tab
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.UserStats
import com.tpov.schoolquiz.shared.feature.app_shell.domain.state.AppShellState
import kotlinx.coroutines.launch

/**
 * Root shell Composable.
 *
 * Wires: ModalNavigationDrawer + Scaffold + TopAppBar + NavigationBar + per-tab Children.
 * Domain → UI: collectAsStateWithLifecycle() on rootComponent.appShellState.
 * UI → Domain: navigator.goTo() + onActiveTabRetap().
 * Drawer sync: 2 LaunchedEffect (bidirectional UI↔domain state sync).
 *
 * @param appVersionName Version string passed from app layer (BuildConfig.VERSION_NAME).
 *        Library modules cannot access BuildConfig.VERSION_NAME directly — app layer passes it.
 *        H8 fix per 06-api-contract.md.
 *
 * Spec journeys: 1 (cold start), 2 (drawer via hamburger), 4 (tab switch),
 * 5 (edge swipe), 7 (scrim close), 8 (swipe close), 9 (re-tap), 10 (back FSM).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppShellScreen(
    rootComponent: DefaultRootComponent,
    appVersionName: String,
    modifier: Modifier = Modifier,
) {
    val state by rootComponent.appShellState.collectAsStateWithLifecycle(
        initialValue = AppShellState.fallback(UserStats.guest())
    )
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    val registry = remember { ScrollToTopRegistry() }

    // --- Drawer sync: UI drawer state → domain ---
    // Handles journeys 5 (edge swipe open), 7 (scrim close), 8 (swipe close).
    // snapshotFlow observes Material3 drawerState.currentValue changes.
    LaunchedEffect(Unit) {
        snapshotFlow { drawerState.currentValue }.collect { drawerValue ->
            if (drawerValue == DrawerValue.Open && !state.isDrawerOpen) {
                rootComponent.navigator.goTo(Destination.OpenDrawer)
            } else if (drawerValue == DrawerValue.Closed && state.isDrawerOpen) {
                rootComponent.navigator.goTo(Destination.CloseDrawer)
            }
        }
    }

    // --- Drawer sync: domain → UI drawer state ---
    // When domain programmatically opens/closes drawer → sync Material3 drawer
    LaunchedEffect(state.isDrawerOpen) {
        if (state.isDrawerOpen) {
            drawerState.open()
        } else {
            drawerState.close()
        }
    }

    CompositionLocalProvider(LocalScrollToTopRegistry provides registry) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = !state.isShopActive,  // SHOP tab: no drawer, no swipe (journeys 5/7/8 disabled)
            drawerContent = {
                // DrawerContent slot — populated in phase-06
                // Placeholder for phase-05:
                UnderConstructionScreen(title = "Drawer (coming soon)")
            },
            modifier = modifier,
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            // B2 fix: displayName uses Labels.kt extensions (not domain fields)
                            Text(text = state.activeSection?.displayName ?: state.activeTab.displayName)
                        },
                        navigationIcon = {
                            // Hamburger: hidden on SHOP tab (spec FR #11, AC 23a)
                            if (!state.isShopActive) {
                                IconButton(onClick = {
                                    coroutineScope.launch { drawerState.open() }
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Menu,
                                        contentDescription = "Open menu",
                                    )
                                }
                            }
                        },
                    )
                },
                bottomBar = {
                    NavigationBar {
                        Tab.values().forEach { tab ->
                            // H6/AC 20: badge param available for future — pass null (MVP BR #15)
                            BrandNavBarItem(
                                tab = tab,
                                selected = state.activeTab == tab,
                                badge = null,  // AC 20: nullable badge surface
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
                // Per-tab content via Decompose Children + Crossfade
                val activeTab = state.activeTab
                Crossfade(
                    targetState = activeTab,
                    animationSpec = tween(300),
                    label = "tab_crossfade",
                ) { tab ->
                    when (tab) {
                        Tab.LOCAL -> Children(
                            stack = rootComponent.localTabComponent.childStack,
                            animation = null,
                        ) { child ->
                            LocalTabContent(child.instance, paddingValues)
                        }
                        Tab.INTERNET -> Children(
                            stack = rootComponent.internetTabComponent.childStack,
                            animation = null,
                        ) { child ->
                            InternetTabContent(child.instance, paddingValues)
                        }
                        Tab.EVENTS -> Children(
                            stack = rootComponent.eventsTabComponent.childStack,
                            animation = null,
                        ) { child ->
                            EventsTabContent(child.instance, paddingValues)
                        }
                        Tab.SHOP -> Children(
                            stack = rootComponent.shopTabComponent.childStack,
                            animation = null,
                        ) { child ->
                            ShopTabContent(child.instance, paddingValues)
                        }
                    }
                }
            }
        }
    }
}

/**
 * NavBar item wrapper with nullable badge surface (AC 20).
 * MVP: badge always null (spec BR #15). Wrapper exposes param in public API for future.
 */
@Composable
private fun BrandNavBarItem(
    tab: Tab,
    selected: Boolean,
    onClick: () -> Unit,
    badge: com.tpov.schoolquiz.shared.feature.app_shell.domain.model.BadgeContent? = null,
    modifier: Modifier = Modifier,
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = {
            Icon(
                imageVector = tab.icon,
                contentDescription = tab.displayName,
            )
        },
        label = { Text(tab.displayName) },
        modifier = modifier,
        // badge: reserved for future use, ignored in MVP
    )
}

// --- Tab content helpers (placeholder, phase-05) ---

@Composable
private fun LocalTabContent(
    screen: com.tpov.schoolquiz.android.feature.app_shell.presentation.screen.LocalScreenComponent,
    paddingValues: androidx.compose.foundation.layout.PaddingValues,
) {
    when (screen) {
        is com.tpov.schoolquiz.android.feature.app_shell.presentation.screen.LocalScreenComponent.Placeholder -> {
            if (screen.config == com.tpov.schoolquiz.shared.feature.app_shell.domain.model.TabConfig.LocalConfig.DesignCatalogRoot
                && com.tpov.schoolquiz.android.feature.app_shell.presentation.BuildConfig.DEBUG) {
                com.tpov.schoolquiz.android.core.designsystem.catalog.DesignCatalogScreen(
                    modifier = androidx.compose.ui.Modifier.padding(paddingValues)
                )
            } else {
                UnderConstructionScreen(
                    title = screen.config.displayName,
                    modifier = androidx.compose.ui.Modifier.padding(paddingValues),
                )
            }
        }
    }
}

// Аналогично: InternetTabContent, EventsTabContent, ShopTabContent — все через UnderConstructionScreen
// Каждый использует screen.config.displayName из Labels.kt
```

Note: все `displayName` и `icon` extensions импортируются из `Labels.kt`. Приватные extension helpers в AppShellScreen.kt убраны — они были дублированием того, что теперь находится в Labels.kt.

### Pattern Invariants

1. **2 LaunchedEffect для drawer sync**: один — `LaunchedEffect(Unit) { snapshotFlow { drawerState.currentValue }.collect { ... } }` (UI→domain); второй — `LaunchedEffect(state.isDrawerOpen) { drawerState.open()/close() }` (domain→UI). Дублирование LaunchedEffect ключей недопустимо.

2. **collectAsStateWithLifecycle, не collectAsState**: `collectAsStateWithLifecycle()` отписывается при Lifecycle.STOPPED. Использование `collectAsState()` — blocker (не уважает lifecycle, leak on background).

3. **Crossfade(tween(300)) для content transition**: только для tab/section switch. Drawer — стандартная Material3 slide анимация (не Crossfade). Не добавлять другие `AnimatedContent` вместо Crossfade без явного ADR.

4. **gesturesEnabled = !state.isShopActive**: Drawer swipe жест отключён на SHOP tab. Hamburger тоже скрыт (TopAppBar). Оба условия из одного и того же `state.isShopActive` — не дублировать логику.

5. **Essenty BackCallback, не OnBackPressedCallback**: System back обрабатывается через `defaultComponentContext()` → Essenty BackHandler → `DefaultRootComponent.backHandler.register(BackCallback(...))`. Дополнительного `OnBackPressedCallback` в `AppShellScreen` быть не должно.
