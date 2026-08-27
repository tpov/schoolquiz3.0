package com.tpov.schoolquiz.android.feature.app_shell.presentation.ui.drawer

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirBg
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirHair
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.DrawerSection
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Tab
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.UserStats
import com.tpov.schoolquiz.shared.feature.app_shell.domain.navigation.Navigator

/**
 * Full drawer content: header + per-tab section list + footer.
 * Passed as drawerContent slot in ModalNavigationDrawer (AppShellScreen).
 *
 * SHOP tab: DrawerSectionList skipped per spec FR #3.
 *
 * The canvas fixes the sheet at 306px on a #08080A ground with a hard right edge — not the
 * Material default width, and no rounded corners to soften it.
 */
@Suppress("FunctionNaming", "LongParameterList", "ktlint:standard:function-naming")
@Composable
fun DrawerContent(
    userStats: UserStats,
    activeTab: Tab,
    activeSection: DrawerSection?,
    navigator: Navigator,
    isDebugBuild: Boolean,
    versionName: String,
    giftBoxCount: Int,
    onVersionTap: () -> Unit,
    onSyncNow: () -> Unit,
    onDismissQuizzes: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    ModalDrawerSheet(
        modifier =
            modifier
                .width(DRAWER_WIDTH)
                .drawBehind {
                    val strokeWidth = 1.dp.toPx()
                    drawLine(
                        color = NoirHair,
                        start = Offset(size.width - strokeWidth / 2f, 0f),
                        end = Offset(size.width - strokeWidth / 2f, size.height),
                        strokeWidth = strokeWidth,
                    )
                },
        // The drawer is black in the drawing; its header and footer are the bands that stand out.
        drawerContainerColor = NoirBg,
        drawerTonalElevation = 0.dp,
        drawerShape = RectangleShape,
    ) {
        DrawerHeader(
            userStats = userStats,
            giftBoxCount = giftBoxCount,
            modifier = Modifier.fillMaxWidth(),
        )

        if (activeTab != Tab.SHOP) {
            DrawerSectionList(
                tab = activeTab,
                userStats = userStats,
                activeSection = activeSection,
                navigator = navigator,
                onDismissQuizzes = onDismissQuizzes,
                modifier = Modifier.weight(1f),
            )
        }

        DrawerFooter(
            navigator = navigator,
            isDebugBuild = isDebugBuild,
            versionName = versionName,
            userStats = userStats,
            onVersionTap = onVersionTap,
            onSyncNow = onSyncNow,
            onDismissQuizzes = onDismissQuizzes,
        )
    }
}

/** Fixed sheet width from the canvas: narrow enough to keep the content visible beside it. */
private val DRAWER_WIDTH = 306.dp
