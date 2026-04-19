package com.tpov.schoolquiz.shared.feature.app_shell.domain.state

import com.tpov.schoolquiz.shared.feature.app_shell.domain.logic.initialEventsTabState
import com.tpov.schoolquiz.shared.feature.app_shell.domain.logic.initialInternetTabState
import com.tpov.schoolquiz.shared.feature.app_shell.domain.logic.initialLocalTabState
import com.tpov.schoolquiz.shared.feature.app_shell.domain.logic.initialShopTabState
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.DrawerSection
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.EventsConfig
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.InternetConfig
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.LocalConfig
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.ShopConfig
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Tab
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.UserStats

/**
 * Aggregated shell state — the single source of truth for the domain FSM.
 *
 * Holds per-tab states, global drawer flag, and user stats (needed for visibility guards).
 * All domain transition functions take [AppShellState] as input and return [TransitionResult].
 *
 * [userStats] is embedded so that transitions can apply isVisible() guards inline
 * without requiring callers to pass stats as a separate parameter.
 */
data class AppShellState(
    val activeTab: Tab,
    val localState: TabState<LocalConfig>,
    val internetState: TabState<InternetConfig>,
    val eventsState: TabState<EventsConfig>,
    val shopState: TabState<ShopConfig>,
    val isDrawerOpen: Boolean,
    val userStats: UserStats,
) {
    /** Convenience: the DrawerSection for the currently active tab. */
    val activeSection: DrawerSection?
        get() = when (activeTab) {
            Tab.LOCAL -> localState.activeSection
            Tab.INTERNET -> internetState.activeSection
            Tab.EVENTS -> eventsState.activeSection
            Tab.SHOP -> null
        }

    /** Convenience: true if active tab is SHOP (which never has a drawer). */
    val isShopActive: Boolean get() = activeTab == Tab.SHOP

    companion object {

        /**
         * Default state: cold-start with given user stats.
         *
         * Domain rules:
         * - Default tab = LOCAL (Business Rule #2).
         * - Default section per tab = [defaultSection](tab, stats) = visibleSections(tab, stats).firstOrNull()
         *   (Spec FR #7 / BR #3).
         * - If visibleSections() is empty for a tab → activeSection = null, stack.active = emptyRootFor(tab).
         *
         * Each tab state is computed by [initialLocalTabState], [initialInternetTabState],
         * [initialEventsTabState], [initialShopTabState].
         */
        fun default(stats: UserStats = UserStats.guest()): AppShellState = AppShellState(
            activeTab = Tab.LOCAL,
            localState = initialLocalTabState(stats),
            internetState = initialInternetTabState(stats),
            eventsState = initialEventsTabState(stats),
            shopState = initialShopTabState(),
            isDrawerOpen = false,
            userStats = stats,
        )

        /**
         * Fallback state for corrupted/unrecognised SavedState.
         * Same as default — domain never crashes on unknown saved state.
         *
         * Domain Test Scenario 39: fallback uses same formula as default, ensuring
         * all per-tab states are valid for the given stats.
         */
        fun fallback(stats: UserStats = UserStats.guest()): AppShellState = default(stats)
    }
}
