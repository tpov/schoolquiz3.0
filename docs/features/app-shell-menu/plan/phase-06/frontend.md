---
phase: phase-06
role: frontend-dev
---

# Phase-06: Frontend Tasks — Drawer Content

## 1. DrawerHeader.kt

```kotlin
package com.tpov.schoolquiz.android.feature.app_shell.presentation.ui.drawer

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tpov.schoolquiz.android.core.designsystem.components.BrandProgressBar
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.UserStats

/**
 * Drawer header: avatar + nickname + premium badge + streak bar + stats row.
 * Spec scope item 4, AC 12 (drawer header), AC 23a-g.
 */
@Composable
fun DrawerHeader(
    userStats: UserStats,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        // Avatar + nickname row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Avatar placeholder (URL loading deferred to future phase with Coil)
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = "Avatar",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            )
            Column {
                Text(
                    text = userStats.nickname.ifBlank { "Guest" },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                if (userStats.hasPremium) {
                    Text(
                        text = "Premium",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // 10-segment streak bar (streakDays 0..10 mapped to 0f..1f)
        BrandProgressBar(
            progress = (userStats.streakDays / 10f).coerceIn(0f, 1f),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = "Серия: ${userStats.streakDays}/10 дней",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        )

        Spacer(Modifier.height(12.dp))

        // Stats row: hearts / gold / stars / nolics
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            StatItem(label = "♥", value = "${userStats.standardHearts}")
            StatItem(label = "💛", value = "${userStats.goldHearts}")
            StatItem(label = "⭐", value = "${userStats.stars}")
            StatItem(label = "◎", value = "${userStats.nolics}")
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
        Text(text = value, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onBackground)
    }
}
```

## 2. DrawerSectionList.kt

```kotlin
package com.tpov.schoolquiz.android.feature.app_shell.presentation.ui.drawer

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tpov.schoolquiz.android.core.designsystem.components.CategoryIcon
import com.tpov.schoolquiz.android.feature.app_shell.presentation.ui.labels.displayName
import com.tpov.schoolquiz.android.feature.app_shell.presentation.ui.labels.icon
import com.tpov.schoolquiz.shared.feature.app_shell.domain.logic.visibleSections
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.DrawerSection
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Tab
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.UserStats
import com.tpov.schoolquiz.shared.feature.app_shell.domain.navigation.Navigator
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Destination

/**
 * Per-tab drawer section list with progressive unlock.
 * Only renders sections returned by visibleSections(tab, stats).
 * Hidden sections are NOT rendered (spec FR #20, 1A decision).
 *
 * B2 fix: displayName/icon come from Labels.kt extensions (presentation layer, not domain).
 * AC 20: uses BrandDrawerItem wrapper which exposes badge: BadgeContent? param.
 */
@Composable
fun DrawerSectionList(
    tab: Tab,
    userStats: UserStats,
    activeSection: DrawerSection?,
    navigator: Navigator,
    modifier: Modifier = Modifier,
) {
    val sections = visibleSections(tab, userStats)

    Column(modifier = modifier) {
        sections.forEach { section ->
            BrandDrawerItem(
                label = section.displayName,
                selected = section == activeSection,
                badge = null,  // AC 20: MVP always null (spec BR #15)
                onClick = {
                    navigator.goTo(Destination.SelectSection(section))
                },
                // Icon via CategoryIcon — uses section.icon from Labels.kt
                // Note: BrandDrawerItem does not expose icon slot by default;
                // override NavigationDrawerItem directly if icon needed:
            )
        }
    }
}
```

Note: `DrawerSection.displayName` и `DrawerSection.icon` — extension properties из `Labels.kt` (phase-05 создаёт их). Map из domain sealed values → display strings in presentation layer. B2 blocker закрыт — все label/icon mappings существуют до compile-gate phase-05.

## 3. DrawerFooter.kt

```kotlin
package com.tpov.schoolquiz.android.feature.app_shell.presentation.ui.drawer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tpov.schoolquiz.android.feature.app_shell.presentation.ui.labels.displayName
import com.tpov.schoolquiz.shared.feature.app_shell.domain.logic.visibleFooterActions
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.DrawerFooterAction
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Destination
import com.tpov.schoolquiz.shared.feature.app_shell.domain.navigation.Navigator

/**
 * Drawer footer: debug/release filtered actions + version label.
 * Spec scope item 4, FR #17 (Design catalog footer action).
 *
 * About tap: H3 fix — spec 0-spec.md:426-430 says About is MVP out of scope and must NOT
 * change domain state. Handled locally via AlertDialog with version info.
 */
@Composable
fun DrawerFooter(
    navigator: Navigator,
    isDebugBuild: Boolean,
    versionName: String,
    modifier: Modifier = Modifier,
) {
    val actions = visibleFooterActions(isDebugBuild)
    val showAboutDialog = remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        HorizontalDivider()
        actions.forEach { action ->
            // AC 20: BrandDrawerItem wrapper exposes badge: BadgeContent? param
            BrandDrawerItem(
                label = action.displayName,
                selected = false,
                badge = null,  // MVP: always null (spec BR #15)
                onClick = {
                    when (action) {
                        DrawerFooterAction.DesignCatalog ->
                            navigator.goTo(Destination.OpenDesignCatalog)
                        DrawerFooterAction.About ->
                            // H3 fix: About is UI-local — no domain navigation per spec 0-spec.md:426-430
                            showAboutDialog.value = true
                    }
                },
            )
        }
        Text(
            text = "v$versionName",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }

    // About dialog: UI-local, does NOT change domain state (spec 0-spec.md:426-430)
    if (showAboutDialog.value) {
        AlertDialog(
            onDismissRequest = { showAboutDialog.value = false },
            title = { Text("О приложении") },
            text = { Text("Версия $versionName") },
            confirmButton = {
                TextButton(onClick = { showAboutDialog.value = false }) {
                    Text("OK")
                }
            },
        )
    }
}

/**
 * Drawer item wrapper with nullable badge surface (AC 20).
 * MVP: badge always null (spec BR #15). Wrapper exposes param in public API for future.
 */
@Composable
fun BrandDrawerItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    badge: com.tpov.schoolquiz.shared.feature.app_shell.domain.model.BadgeContent? = null,
    modifier: Modifier = Modifier,
) {
    NavigationDrawerItem(
        label = { Text(label) },
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        // badge: reserved for future use, ignored in MVP
    )
}
```

## 4. DrawerContent.kt

```kotlin
package com.tpov.schoolquiz.android.feature.app_shell.presentation.ui.drawer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.DrawerSection
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Tab
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.UserStats
import com.tpov.schoolquiz.shared.feature.app_shell.domain.navigation.Navigator

/**
 * Full drawer content: header + per-tab section list + footer.
 * Passed as drawerContent slot in ModalNavigationDrawer.
 */
@Composable
fun DrawerContent(
    userStats: UserStats,
    activeTab: Tab,
    activeSection: DrawerSection?,
    navigator: Navigator,
    isDebugBuild: Boolean,
    versionName: String,
    modifier: Modifier = Modifier,
) {
    ModalDrawerSheet(modifier = modifier) {
        DrawerHeader(userStats = userStats, modifier = Modifier.fillMaxWidth())

        // SHOP tab: no sections (spec FR #3)
        if (activeTab != Tab.SHOP) {
            DrawerSectionList(
                tab = activeTab,
                userStats = userStats,
                activeSection = activeSection,
                navigator = navigator,
                modifier = Modifier.weight(1f),
            )
        }

        DrawerFooter(
            navigator = navigator,
            isDebugBuild = isDebugBuild,
            versionName = versionName,
        )
    }
}
```

## 5. Update AppShellScreen.kt (drawerContent slot)

В файле `AppShellScreen.kt` из phase-05 заменить drawer placeholder.

H8 fix: `BuildConfig.VERSION_NAME` недоступен в library module `android/feature/app-shell/presentation`.
`appVersionName` передаётся как параметр в `AppShellScreen` из app layer (phase-07 MainActivity).

Было:
```kotlin
drawerContent = {
    UnderConstructionScreen(title = "Drawer (coming soon)")
},
```

Стало:
```kotlin
drawerContent = {
    DrawerContent(
        userStats = state.userStats,
        activeTab = state.activeTab,
        activeSection = state.activeSection,
        navigator = rootComponent.navigator,
        isDebugBuild = BuildConfig.DEBUG,
        versionName = appVersionName,  // H8: passed from app layer, not BuildConfig.VERSION_NAME
    )
},
```

`AppShellScreen` сигнатура (из phase-05) уже содержит `appVersionName: String` параметр. Phase-06 только использует его — не добавляет новый параметр.

### Pattern Invariants

1. **visibleSections вызывается в Composable** — на каждой recomposition. Это чистая pure function — нет side effects, нет state mutation. Никакого кэширования/remember для `visibleSections` в phase-06 (оптимизация — future if needed).

2. **No direct Firebase imports in drawer files**: drawer читает только `UserStats` из `state.userStats` — никакого Firestore SDK.

3. **DrawerSection.SHOP check**: `DrawerContent` явно пропускает `DrawerSectionList` для `Tab.SHOP` (spec FR #3). Не добавлять conditional через `visibleSections` — явная проверка `activeTab != Tab.SHOP`.

4. **Tap Design Catalog → Destination.OpenDesignCatalog**: `DrawerFooter` должен вызывать `navigator.goTo(Destination.OpenDesignCatalog)` — не `navigator.goTo(Destination.SelectSection(DesignCatalog))` (это footer action, not a section per spec FR #17).
