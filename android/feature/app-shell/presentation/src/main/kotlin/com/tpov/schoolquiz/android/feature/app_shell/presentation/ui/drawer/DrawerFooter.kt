package com.tpov.schoolquiz.android.feature.app_shell.presentation.ui.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tpov.schoolquiz.android.core.designsystem.components.schoolQuizDesignLightBorderColor
import com.tpov.schoolquiz.android.core.designsystem.noir.LocalNoirAccent
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirChip
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirChipTone
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirShapeMd
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT2
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirType
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
@Suppress("FunctionNaming", "LongParameterList", "ktlint:standard:function-naming")
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
        HorizontalDivider(color = schoolQuizDesignLightBorderColor())
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
            modifier =
                Modifier
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
 * One row of the drawer.
 *
 * Selection is carried by the accent on the label and a hairline block behind it, not by a filled
 * pill: on black a filled row competes with the content it is meant to lead to.
 *
 * [badge] is always null for now (spec BR #15) but stays in the signature: the surface is what the
 * unread counters will hang off, and adding it later would touch every call site.
 */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
fun BrandDrawerItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    badge: BadgeContent? = null,
    modifier: Modifier = Modifier,
) {
    val accent = LocalNoirAccent.current
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(NoirShapeMd)
            .background(if (selected) accent.copy(alpha = 0.10f) else Color.Transparent)
            .clickable(onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = label,
            style = NoirType.rowTitle.copy(color = if (selected) accent else NoirT2),
            modifier = Modifier.weight(1f),
        )
        badge?.let { NoirChip(text = it.label, tone = NoirChipTone.Accent) }
    }
}

/** Both badge shapes read as one short label on a chip. */
private val BadgeContent.label: String
    get() =
        when (this) {
            is BadgeContent.Count -> value.toString()
            is BadgeContent.Text -> value
        }
