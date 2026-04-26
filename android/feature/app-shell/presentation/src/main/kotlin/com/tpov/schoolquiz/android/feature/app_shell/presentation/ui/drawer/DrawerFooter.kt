package com.tpov.schoolquiz.android.feature.app_shell.presentation.ui.drawer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.BadgeContent
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Destination
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.DrawerFooterAction
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.UserStats
import com.tpov.schoolquiz.shared.feature.app_shell.domain.navigation.Navigator

/**
 * Drawer footer: debug/release filtered actions + version label.
 * Spec scope item 4, FR #17 (Design catalog footer action).
 *
 * H3 fix: About tap shows local AlertDialog — does NOT change domain state (spec 0-spec.md:426-430).
 * H8 fix: versionName passed as param from app layer, not BuildConfig.VERSION_NAME.
 */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
fun DrawerFooter(
    navigator: Navigator,
    isDebugBuild: Boolean,
    versionName: String,
    userStats: UserStats,
    onVersionTap: () -> Unit,
    onSyncNow: () -> Unit,
    onDismissQuizzes: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val actions = visibleFooterActions(isDebugBuild, userStats)
    val showAboutDialog = remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        HorizontalDivider()
        actions.forEach { action ->
            BrandDrawerItem(
                label = action.displayName,
                selected = false,
                badge = null,
                onClick = {
                    when (action) {
                        DrawerFooterAction.DesignCatalog -> {
                            onDismissQuizzes()
                            navigator.goTo(Destination.OpenDesignCatalog)
                        }
                        DrawerFooterAction.SyncNow -> onSyncNow()
                        DrawerFooterAction.About ->
                            showAboutDialog.value = true
                    }
                },
            )
        }
        Text(
            text = "v$versionName",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            modifier = Modifier
                .clickable(onClick = onVersionTap)
                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }

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
 * MVP: badge always null (spec BR #15). Exposes badge param in public API for future use.
 */
@Suppress("FunctionNaming", "ktlint:standard:function-naming", "UnusedParameter")
@Composable
fun BrandDrawerItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    badge: BadgeContent? = null,
    modifier: Modifier = Modifier,
) {
    NavigationDrawerItem(
        label = { Text(label) },
        selected = selected,
        onClick = onClick,
        modifier = modifier,
    )
}
