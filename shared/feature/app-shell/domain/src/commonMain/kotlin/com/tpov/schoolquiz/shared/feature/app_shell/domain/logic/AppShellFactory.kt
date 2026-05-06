package com.tpov.schoolquiz.shared.feature.app_shell.domain.logic

import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.EventsConfig
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.InternetConfig
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.LocalConfig
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.ShopConfig
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Tab
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.UserStats
import com.tpov.schoolquiz.shared.feature.app_shell.domain.state.NavStack
import com.tpov.schoolquiz.shared.feature.app_shell.domain.state.TabState

/**
 * Pure factory functions for constructing initial per-tab states.
 *
 * Extracted from AppShellState.default() so that [AppShellState.default] and
 * [AppShellTransitions.onSwitchTab] (first activation) share the same formula.
 *
 * Spec FR #7 / BR #3: default section = [defaultSection](tab, stats) = visibleSections.firstOrNull()
 * If no visible sections → activeSection=null, stack.active = [emptyRootFor](tab).
 */

/**
 * Returns the initial [TabState] for the LOCAL tab given [stats].
 *
 * LOCAL always has at least MyQuests visible (emptyMap), so activeSection is never null here.
 * Kept parameterised for consistency with other tabs.
 */
fun initialLocalTabState(stats: UserStats): TabState<LocalConfig> {
    val section = defaultSection(Tab.LOCAL, stats)
    return if (section != null) {
        TabState(activeSection = section, stack = NavStack(active = rootOf(section) as LocalConfig))
    } else {
        TabState(activeSection = null, stack = NavStack(active = LocalConfig.EmptyRoot))
    }
}

/**
 * Returns the initial [TabState] for the INTERNET tab given [stats].
 *
 * For guest stats: Profile (first visible, emptyMap) is the default.
 * For skill >= 3000: Arena is first visible.
 */
fun initialInternetTabState(stats: UserStats): TabState<InternetConfig> {
    val section = defaultSection(Tab.INTERNET, stats)
    return if (section != null) {
        TabState(activeSection = section, stack = NavStack(active = rootOf(section) as InternetConfig))
    } else {
        TabState(activeSection = null, stack = NavStack(active = InternetConfig.EmptyRoot))
    }
}

/**
 * Returns the initial [TabState] for the EVENTS tab given [stats].
 *
 * For guest stats: QualifierTournament is the first visible event.
 * For skill >= 10000: Minigames also becomes visible.
 * For full qualification: ActiveEvents also becomes visible.
 */
fun initialEventsTabState(stats: UserStats): TabState<EventsConfig> {
    val section = defaultSection(Tab.EVENTS, stats)
    return if (section != null) {
        TabState(activeSection = section, stack = NavStack(active = rootOf(section) as EventsConfig))
    } else {
        TabState(activeSection = null, stack = NavStack(active = EventsConfig.EmptyRoot))
    }
}

/**
 * Returns the initial [TabState] for the SHOP tab.
 *
 * SHOP is special: always ShopRoot, never has a DrawerSection.
 */
fun initialShopTabState(): TabState<ShopConfig> =
    TabState(activeSection = null, stack = NavStack(active = ShopConfig.ShopRoot))
