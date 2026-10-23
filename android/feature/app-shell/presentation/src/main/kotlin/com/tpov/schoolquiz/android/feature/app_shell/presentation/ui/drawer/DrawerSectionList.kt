package com.tpov.schoolquiz.android.feature.app_shell.presentation.ui.drawer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tpov.schoolquiz.android.feature.app_shell.presentation.ui.labels.displayName
import com.tpov.schoolquiz.shared.feature.app_shell.domain.logic.visibleSections
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Destination
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.DrawerSection
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Tab
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.UserStats
import com.tpov.schoolquiz.shared.feature.app_shell.domain.navigation.Navigator

/**
 * Per-tab drawer section list with progressive unlock (spec FR #20, AC 23a-g).
 * Renders only sections returned by visibleSections(tab, stats) — hidden sections NOT rendered.
 * AC 20: uses BrandDrawerItem wrapper with badge: BadgeContent? param.
 */
@Suppress("FunctionNaming", "LongParameterList", "ktlint:standard:function-naming")
@Composable
fun DrawerSectionList(
    tab: Tab,
    isSignedIn: Boolean,
    userStats: UserStats,
    activeSection: DrawerSection?,
    navigator: Navigator,
    onDismissQuizzes: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val sections = visibleSections(tab, userStats, isSignedIn)

    // Секций стало больше, чем влезает между шапкой и футером на низких экранах: без прокрутки
    // хвост списка обрезается футером и оказывается недостижим вовсе (TZ06C: кураторская
    // поверхность живёт внизу списка).
    Column(modifier = modifier.verticalScroll(rememberScrollState())) {
        sections.forEach { section ->
            BrandDrawerItem(
                label = section.displayName,
                selected = section == activeSection,
                badge = null,
                onClick = {
                    onDismissQuizzes()
                    navigator.goTo(Destination.SelectSection(section))
                },
                divided = true,
            )
        }
    }
}
