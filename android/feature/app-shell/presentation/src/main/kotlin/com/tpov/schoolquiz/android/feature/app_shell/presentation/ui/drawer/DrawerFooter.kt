package com.tpov.schoolquiz.android.feature.app_shell.presentation.ui.drawer

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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tpov.schoolquiz.android.core.designsystem.noir.LocalNoirAccent
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirChip
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirChipTone
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirHair
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT2
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirT3
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirType
import com.tpov.schoolquiz.android.feature.app_shell.presentation.R
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
        HorizontalDivider(color = NoirHair)
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
                monoCaps = true,
            )
        }
        Text(
            text = "v$versionName",
            style = NoirType.button,
            color = NoirT3,
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
            title = { Text(stringResource(R.string.footer_about)) },
            text = { Text(stringResource(R.string.about_version, versionName)) },
            confirmButton = {
                TextButton(onClick = { showAboutDialog.value = false }) {
                    Text(stringResource(R.string.dialog_ok))
                }
            },
        )
    }
}

/**
 * One row of the drawer.
 *
 * Selection is a 3px accent rail on the left edge plus a faint accent wash — the canvas draws it
 * as a rail, not a filled pill: on black a filled row competes with the content it is meant to
 * lead to. [divided] adds the hairline under the row; section lists use it, footers do not.
 * [monoCaps] switches to the footer's small uppercase mono label instead of the section's
 * sentence-case row.
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
    divided: Boolean = false,
    monoCaps: Boolean = false,
) {
    val accent = LocalNoirAccent.current
    Row(
        modifier
            .fillMaxWidth()
            .drawBehind {
                if (selected) {
                    drawRect(color = accent.copy(alpha = 0.07f))
                    drawRect(
                        color = accent,
                        topLeft = Offset.Zero,
                        size = Size(3.dp.toPx(), size.height),
                    )
                }
                if (divided) {
                    drawLine(
                        color = NoirHair,
                        start = Offset(0f, size.height),
                        end = Offset(size.width, size.height),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
            }
            .clickable(onClick = onClick)
            .heightIn(min = if (monoCaps) 46.dp else 52.dp)
            .padding(horizontal = 16.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (monoCaps) 12.dp else 13.dp),
    ) {
        Text(
            text = if (monoCaps) label.uppercase() else label,
            style =
                if (monoCaps) {
                    NoirType.button.copy(fontSize = 10.5.sp, color = NoirT3)
                } else {
                    NoirType.rowTitle.copy(
                        fontWeight = FontWeight.Medium,
                        color = if (selected) accent else NoirT2,
                    )
                },
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
