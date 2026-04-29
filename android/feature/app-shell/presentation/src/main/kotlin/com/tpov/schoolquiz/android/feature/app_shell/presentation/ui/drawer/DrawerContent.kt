package com.tpov.schoolquiz.android.feature.app_shell.presentation.ui.drawer

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
 * Passed as drawerContent slot in ModalNavigationDrawer (AppShellScreen).
 *
 * SHOP tab: DrawerSectionList skipped per spec FR #3.
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
    onVersionTap: () -> Unit,
    onSyncNow: () -> Unit,
    onDismissQuizzes: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    ModalDrawerSheet(modifier = modifier) {
        DrawerHeader(
            userStats = userStats,
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
