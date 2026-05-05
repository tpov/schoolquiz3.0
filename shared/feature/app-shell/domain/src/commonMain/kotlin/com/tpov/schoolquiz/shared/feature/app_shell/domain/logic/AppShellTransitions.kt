package com.tpov.schoolquiz.shared.feature.app_shell.domain.logic

import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.DeepLink
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Destination
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.DrawerSection
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.EventsConfig
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.InternetConfig
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.LocalConfig
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.RetapOutcome
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.RootEvent
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Tab
import com.tpov.schoolquiz.shared.feature.app_shell.domain.state.AppShellState
import com.tpov.schoolquiz.shared.feature.app_shell.domain.state.NavStack
import com.tpov.schoolquiz.shared.feature.app_shell.domain.state.TabState
import com.tpov.schoolquiz.shared.feature.app_shell.domain.state.TransitionResult

// ---------------------------------------------------------------------------
// Root config helpers — returns the default root NavStack for each section
// ---------------------------------------------------------------------------

/** Root NavStack for the given DrawerSection (no backStack). */
fun rootStackForSection(section: DrawerSection): NavStack<*> = when (section) {
    is DrawerSection.LocalSection -> rootLocalStackForSection(section)
    is DrawerSection.InternetSection -> rootInternetStackForSection(section)
    is DrawerSection.EventsSection -> rootEventsStackForSection(section)
}

fun rootLocalStackForSection(section: DrawerSection.LocalSection): NavStack<LocalConfig> =
    when (section) {
        DrawerSection.LocalSection.MyQuests -> NavStack(LocalConfig.MyQuestsRoot)
        DrawerSection.LocalSection.HomeQuests -> NavStack(LocalConfig.HomeQuestsRoot)
        DrawerSection.LocalSection.Archive -> NavStack(LocalConfig.ArchiveRoot)
        DrawerSection.LocalSection.ReviewQueue -> NavStack(LocalConfig.ReviewQueueRoot)
        DrawerSection.LocalSection.Settings -> NavStack(LocalConfig.SettingsRoot)
    }

fun rootInternetStackForSection(section: DrawerSection.InternetSection): NavStack<InternetConfig> =
    when (section) {
        DrawerSection.InternetSection.Arena -> NavStack(InternetConfig.ArenaRoot)
        DrawerSection.InternetSection.Catalog -> NavStack(InternetConfig.CatalogRoot)
        DrawerSection.InternetSection.Qualifications -> NavStack(InternetConfig.QualificationsRoot)
        DrawerSection.InternetSection.Profile -> NavStack(InternetConfig.ProfileRoot)
        DrawerSection.InternetSection.Social -> NavStack(InternetConfig.SocialRoot)
        DrawerSection.InternetSection.Leaderboard -> NavStack(InternetConfig.LeaderboardRoot)
    }

fun rootEventsStackForSection(section: DrawerSection.EventsSection): NavStack<EventsConfig> =
    when (section) {
        DrawerSection.EventsSection.ActiveEvents -> NavStack(EventsConfig.ActiveEventsRoot)
        DrawerSection.EventsSection.Minigames -> NavStack(EventsConfig.MinigamesRoot)
    }

// ---------------------------------------------------------------------------
// navigate — main dispatcher for all Destination types
// ---------------------------------------------------------------------------

/**
 * Apply a [Destination] to the current [AppShellState], returning a [TransitionResult].
 *
 * Pure function: no side effects, no throw.
 * Visibility guards use state.userStats (Business Rule #20 / FR #20).
 *
 * Implements:
 * - Back-policy FSM (Business Rule #9)
 * - Drawer visibility guard for SHOP (Business Rule #5)
 * - Section type-safe binding to tab (Business Rule #6)
 * - Tab switch with state preservation (Business Rule #8)
 * - Re-tap via SwitchTab(same tab) delegates to retap FSM
 * - Section visibility pre-guard (Business Rule #20)
 */
fun navigate(state: AppShellState, destination: Destination): TransitionResult =
    when (destination) {
        Destination.Back -> onBack(state)
        is Destination.SwitchTab -> onSwitchTab(state, destination.tab)
        is Destination.SelectSection -> onSelectSection(state, destination.section)
        Destination.OpenDrawer -> onOpenDrawer(state)
        Destination.CloseDrawer -> TransitionResult(state.copy(isDrawerOpen = false))
        Destination.OpenDesignCatalog -> onOpenDesignCatalog(state)
        Destination.OpenQuestCreate -> onOpenQuestCreate(state)
    }

// ---------------------------------------------------------------------------
// onBack — back-policy FSM (State Matrix: back-policy rows 1-6)
// ---------------------------------------------------------------------------

/**
 * Back-policy FSM:
 * 1. drawer open → close drawer (row 1)
 * 2. drawer closed, backStack not empty → pop (row 2)
 * 3. drawer closed, backStack empty, tab != LOCAL → switch to LOCAL (rows 4,5,6)
 * 4. drawer closed, backStack empty, tab == LOCAL → emit RootEvent.SystemBack (row 3)
 */
fun onBack(state: AppShellState): TransitionResult {
    // Step 1: drawer open → close it
    if (state.isDrawerOpen) {
        return TransitionResult(state.copy(isDrawerOpen = false))
    }

    // Step 2: backStack not empty → pop current tab's stack
    val poppedState = popCurrentTabStack(state)
    if (poppedState != null) {
        return TransitionResult(poppedState)
    }

    // Step 3 & 4: backStack empty
    return if (state.activeTab != Tab.LOCAL) {
        // Switch to LOCAL, restore its saved state
        TransitionResult(state.copy(activeTab = Tab.LOCAL))
    } else {
        // Already on LOCAL root with empty stack — emit SystemBack
        TransitionResult(
            newState = state,
            events = listOf(RootEvent.SystemBack),
        )
    }
}

/** Returns new AppShellState with active tab's stack popped, or null if already at root. */
private fun popCurrentTabStack(state: AppShellState): AppShellState? {
    return when (state.activeTab) {
        Tab.LOCAL -> {
            val popped = state.localState.stack.pop() ?: return null
            state.copy(localState = state.localState.copy(stack = popped))
        }
        Tab.INTERNET -> {
            val popped = state.internetState.stack.pop() ?: return null
            state.copy(internetState = state.internetState.copy(stack = popped))
        }
        Tab.EVENTS -> {
            val popped = state.eventsState.stack.pop() ?: return null
            state.copy(eventsState = state.eventsState.copy(stack = popped))
        }
        Tab.SHOP -> {
            // Shop always has empty backStack; pop returns null
            null
        }
    }
}

// ---------------------------------------------------------------------------
// onSwitchTab — tab-switch FSM (State Matrix: tab-switch rows 1-2)
// ---------------------------------------------------------------------------

/**
 * Tab-switch FSM:
 * - row 1: target == current → delegate to retap FSM
 * - row 2: target != current → preserve current TabState, restore target TabState
 *
 * Business Rule #8: TabState preserved on every switch (activeSection + childStack).
 */
fun onSwitchTab(state: AppShellState, targetTab: Tab): TransitionResult {
    return if (targetTab == state.activeTab) {
        // Same tab → retap; outcome embedded via identity of stack
        val (newState, _) = onActiveTabRetap(state, targetTab)
        TransitionResult(newState)
    } else {
        // Different tab → preserve current, restore target
        TransitionResult(state.copy(activeTab = targetTab, isDrawerOpen = false))
    }
}

// ---------------------------------------------------------------------------
// onActiveTabRetap — re-tap FSM (State Matrix: re-tap rows 1-2)
// ---------------------------------------------------------------------------

/**
 * Re-tap active tab FSM:
 * - row 1: backStack not empty → POP_TO_ROOT, clear backStack
 * - row 2: backStack empty → NO_OP
 *
 * Returns Pair<AppShellState, RetapOutcome>.
 * State is the new state (possibly with backStack cleared).
 */
fun onActiveTabRetap(state: AppShellState, tab: Tab): Pair<AppShellState, RetapOutcome> {
    val isAtRoot = when (tab) {
        Tab.LOCAL -> state.localState.stack.isAtRoot
        Tab.INTERNET -> state.internetState.stack.isAtRoot
        Tab.EVENTS -> state.eventsState.stack.isAtRoot
        Tab.SHOP -> state.shopState.stack.isAtRoot
    }

    return if (!isAtRoot) {
        // POP_TO_ROOT: clear backStack, keep the bottom-most config as active
        val newState = when (tab) {
            Tab.LOCAL -> {
                val s = state.localState.stack
                state.copy(
                    localState = state.localState.copy(
                        stack = s.replaceWithRoot(s.backStack.first()),
                    ),
                )
            }
            Tab.INTERNET -> {
                val s = state.internetState.stack
                state.copy(
                    internetState = state.internetState.copy(
                        stack = s.replaceWithRoot(s.backStack.first()),
                    ),
                )
            }
            Tab.EVENTS -> {
                val s = state.eventsState.stack
                state.copy(
                    eventsState = state.eventsState.copy(
                        stack = s.replaceWithRoot(s.backStack.first()),
                    ),
                )
            }
            Tab.SHOP -> state // Shop has no real backStack in MVP
        }
        Pair(newState, RetapOutcome.POP_TO_ROOT)
    } else {
        Pair(state, RetapOutcome.NO_OP)
    }
}

// ---------------------------------------------------------------------------
// onSelectSection — section-switch FSM (State Matrix: drawer-section rows 1-4)
// ---------------------------------------------------------------------------

/**
 * Drawer section switch FSM with visibility pre-guard.
 *
 * Pre-guard (Business Rule #20): rows 1 and 2 check isVisible before acting.
 * If !isVisible(section, state.userStats) → silent no-op (protects against deep links).
 *
 * - row 1: section.tab != activeTab → [guard] → switchTab, set section, close drawer
 * - row 2: section.tab == activeTab, section != activeSection → [guard] → set section, replace stack, close drawer
 * - row 3: section.tab == activeTab, section == activeSection, drawer open → close drawer only (no guard needed)
 * - row 4: section.tab == activeTab, section == activeSection, drawer closed → no-op (no guard needed)
 */
fun onSelectSection(state: AppShellState, section: DrawerSection): TransitionResult {
    val isSameTab = section.tab == state.activeTab
    val isSameSection = state.activeSection == section

    return if (!isSameTab) {
        // Row 1: cross-tab — visibility pre-guard
        if (!isVisible(section, state.userStats)) return TransitionResult(state)
        val stateWithTabSwitch = state.copy(activeTab = section.tab, isDrawerOpen = false)
        val stateWithSection = setTabSection(stateWithTabSwitch, section)
        TransitionResult(stateWithSection)
    } else if (!isSameSection) {
        // Row 2: same tab, different section — visibility pre-guard
        if (!isVisible(section, state.userStats)) return TransitionResult(state)
        val stateWithSection = setTabSection(state, section)
        TransitionResult(stateWithSection.copy(isDrawerOpen = false))
    } else if (state.isDrawerOpen) {
        // Row 3: same tab, same section, drawer open → only close drawer
        // No guard: active section was already visible when it became active
        TransitionResult(state.copy(isDrawerOpen = false))
    } else {
        // Row 4: same tab, same section, drawer already closed → no-op
        TransitionResult(state)
    }
}

/** Sets the section and replaces the stack with the root of that section. */
private fun setTabSection(state: AppShellState, section: DrawerSection): AppShellState =
    when (section) {
        is DrawerSection.LocalSection -> state.copy(
            localState = TabState(
                activeSection = section,
                stack = rootLocalStackForSection(section),
            ),
        )
        is DrawerSection.InternetSection -> state.copy(
            internetState = TabState(
                activeSection = section,
                stack = rootInternetStackForSection(section),
            ),
        )
        is DrawerSection.EventsSection -> state.copy(
            eventsState = TabState(
                activeSection = section,
                stack = rootEventsStackForSection(section),
            ),
        )
    }

// ---------------------------------------------------------------------------
// onOpenDrawer
// ---------------------------------------------------------------------------

/**
 * Open drawer.
 * Domain rule: no-op (guard) if activeTab == SHOP.
 */
fun onOpenDrawer(state: AppShellState): TransitionResult =
    if (state.activeTab == Tab.SHOP) {
        // Guard: Shop has no drawer
        TransitionResult(state)
    } else {
        TransitionResult(state.copy(isDrawerOpen = true))
    }

// ---------------------------------------------------------------------------
// onOpenDesignCatalog — footer action (debug builds only)
// ---------------------------------------------------------------------------

/**
 * Opens the design catalog tool.
 *
 * Result: activeTab=LOCAL, localState.activeSection=null,
 *         localState.stack.active=LocalConfig.DesignCatalogRoot, backStack=[],
 *         isDrawerOpen=false.
 *
 * Domain Test Scenario 41.
 * Note: DesignCatalog is a [DrawerFooterAction], not a DrawerSection.
 * This destination is only dispatched in debug builds (caller must check [visibleFooterActions]).
 */
fun onOpenDesignCatalog(state: AppShellState): TransitionResult =
    TransitionResult(
        state.copy(
            activeTab = Tab.LOCAL,
            localState = TabState(
                activeSection = null,
                stack = NavStack(active = LocalConfig.DesignCatalogRoot),
            ),
            isDrawerOpen = false,
        ),
    )

// ---------------------------------------------------------------------------
// onOpenQuestCreate — FAB destination (push QuestCreateRoot onto LOCAL stack)
// ---------------------------------------------------------------------------

/**
 * Pushes [LocalConfig.QuestCreateRoot] onto the LOCAL tab's stack on top of current active.
 *
 * Differs from [onOpenDesignCatalog] (replaces entire stack) — this is a push, preserving
 * the existing active config in backStack so Back returns to the previous screen
 * (typically [LocalConfig.MyQuestsRoot]).
 *
 * **Re-tap guard (Decision #47)**: if `state.localState.stack.active == LocalConfig.QuestCreateRoot`
 * — no-op (state unchanged). Prevents stacking duplicate QuestCreateRoot entries when
 * the user taps the FAB while already on the create screen.
 *
 * **Cross-tab behavior**: if `activeTab != Tab.LOCAL`, switches to LOCAL first,
 * then pushes `QuestCreateRoot` onto whatever was the LOCAL active (preserving current
 * `localState` content in backStack). Spec: home-and-my-quests `0-spec.md` scenario 41c.
 *
 * Spec: home-and-my-quests `0-spec.md` Decisions #41, #45, #47, #48 + scenarios 41a-41e.
 */
fun onOpenQuestCreate(state: AppShellState): TransitionResult {
    // Re-tap guard (Decision #47): already on QuestCreateRoot → no-op.
    // Check only localState.stack.active — independent of activeTab. If LOCAL stack already
    // tops with QuestCreateRoot but user is on EVENTS, switching back to LOCAL via this
    // destination should still no-op (no duplicate push).
    if (state.localState.stack.active == LocalConfig.QuestCreateRoot) {
        return TransitionResult(state)
    }

    val pushedStack = state.localState.stack.push(LocalConfig.QuestCreateRoot)
    return TransitionResult(
        state.copy(
            activeTab = Tab.LOCAL,
            localState = state.localState.copy(stack = pushedStack),
            isDrawerOpen = false,
        ),
    )
}

// ---------------------------------------------------------------------------
// onDeepLink — stub architectural hook (MVP: no registered patterns)
// ---------------------------------------------------------------------------

/**
 * Deep link handler.
 * MVP stub: accepts any URI without navigation side-effects.
 * Returns state unchanged; future feature phases will parse URI and navigate.
 */
@Suppress("UNUSED_PARAMETER")
fun onDeepLink(state: AppShellState, deepLink: DeepLink): TransitionResult =
    TransitionResult(state)

// ---------------------------------------------------------------------------
// fallbackState
// ---------------------------------------------------------------------------

/**
 * Recovery state for corrupted SavedState.
 * Always returns a valid default state — application never crashes.
 */
fun fallbackState(): AppShellState = AppShellState.fallback()
