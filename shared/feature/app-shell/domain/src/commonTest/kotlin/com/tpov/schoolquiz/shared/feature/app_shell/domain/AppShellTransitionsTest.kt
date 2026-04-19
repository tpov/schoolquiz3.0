package com.tpov.schoolquiz.shared.feature.app_shell.domain

import com.tpov.schoolquiz.shared.feature.app_shell.domain.logic.navigate
import com.tpov.schoolquiz.shared.feature.app_shell.domain.logic.onActiveTabRetap
import com.tpov.schoolquiz.shared.feature.app_shell.domain.logic.onBack
import com.tpov.schoolquiz.shared.feature.app_shell.domain.logic.onDeepLink
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.DeepLink
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Destination
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.DrawerSection
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.InternetConfig
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.LocalConfig
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.RetapOutcome
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.RootEvent
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Tab
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.UserStats
import com.tpov.schoolquiz.shared.feature.app_shell.domain.state.AppShellState
import com.tpov.schoolquiz.shared.feature.app_shell.domain.state.NavStack
import com.tpov.schoolquiz.shared.feature.app_shell.domain.state.TabState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for AppShellTransitions pure functions.
 *
 * Coverage:
 * - Domain Test Scenarios 2-13, 15, 17-21
 * - State Matrix: back-policy (rows 1-6), re-tap (rows 1-2), tab-switch (rows 1-2),
 *   drawer-section (rows 1-4)
 *
 * (Scenario 1 = cold start → AppShellStateTest; Scenario 14 = UserStats.guest() → UserStatsTest;
 *  Scenario 16 = fallback → AppShellStateTest)
 */
class AppShellTransitionsTest {

    // -----------------------------------------------------------------------
    // Helper builders
    // -----------------------------------------------------------------------

    private fun defaultState() = AppShellState.default()

    private fun stateWithInternetProfile(): AppShellState {
        val base = defaultState()
        return base.copy(
            activeTab = Tab.INTERNET,
            internetState = TabState(
                activeSection = DrawerSection.InternetSection.Profile,
                stack = NavStack(active = InternetConfig.ProfileRoot),
            ),
        )
    }

    // -----------------------------------------------------------------------
    // Domain Test Scenario 2 — tab switch preserves local state
    // -----------------------------------------------------------------------

    @Test
    fun `scenario 2 given LOCAL backStack empty when switchTab INTERNET then activeTab is INTERNET`() {
        val state = defaultState()
        val result = navigate(state, Destination.SwitchTab(Tab.INTERNET))
        assertEquals(Tab.INTERNET, result.newState.activeTab)
    }

    @Test
    fun `scenario 2 given LOCAL backStack empty when switchTab INTERNET then internet activeSection is Qualifications`() {
        // FR #7 / BR #3: defaultSection(INTERNET, guestStats) = Qualifications (first visible)
        val state = defaultState()
        val result = navigate(state, Destination.SwitchTab(Tab.INTERNET))
        assertEquals(DrawerSection.InternetSection.Qualifications, result.newState.internetState.activeSection)
    }

    @Test
    fun `scenario 2 given LOCAL backStack empty when switchTab INTERNET then local TabState preserved`() {
        val state = defaultState()
        val result = navigate(state, Destination.SwitchTab(Tab.INTERNET))
        assertEquals(DrawerSection.LocalSection.MyQuests, result.newState.localState.activeSection)
    }

    // -----------------------------------------------------------------------
    // Domain Test Scenario 3 — section switch within same tab
    // -----------------------------------------------------------------------

    @Test
    fun `scenario 3 given INTERNET Arena when selectSection Profile then activeSection is Profile`() {
        val state = defaultState().copy(activeTab = Tab.INTERNET)
        val result = navigate(state, Destination.SelectSection(DrawerSection.InternetSection.Profile))
        assertEquals(DrawerSection.InternetSection.Profile, result.newState.internetState.activeSection)
    }

    @Test
    fun `scenario 3 given INTERNET Arena when selectSection Profile then stack active is ProfileRoot`() {
        val state = defaultState().copy(activeTab = Tab.INTERNET)
        val result = navigate(state, Destination.SelectSection(DrawerSection.InternetSection.Profile))
        assertEquals(InternetConfig.ProfileRoot, result.newState.internetState.stack.active)
    }

    @Test
    fun `scenario 3 given INTERNET Arena when selectSection Profile then backStack is empty`() {
        val state = defaultState().copy(activeTab = Tab.INTERNET)
        val result = navigate(state, Destination.SelectSection(DrawerSection.InternetSection.Profile))
        assertTrue(result.newState.internetState.stack.backStack.isEmpty())
    }

    @Test
    fun `scenario 3 given INTERNET Arena when selectSection Profile then drawer is closed`() {
        val state = defaultState().copy(activeTab = Tab.INTERNET, isDrawerOpen = true)
        val result = navigate(state, Destination.SelectSection(DrawerSection.InternetSection.Profile))
        assertFalse(result.newState.isDrawerOpen)
    }

    // -----------------------------------------------------------------------
    // Domain Test Scenario 4 — tab switch preserves internet state
    // -----------------------------------------------------------------------

    @Test
    fun `scenario 4 given Internet Profile when switchTab LOCAL then switchTab INTERNET then profile restored`() {
        val state = stateWithInternetProfile()

        // Step 1: go LOCAL
        val afterLocal = navigate(state, Destination.SwitchTab(Tab.LOCAL)).newState
        assertEquals(Tab.LOCAL, afterLocal.activeTab)

        // Step 2: go INTERNET again
        val afterInternet = navigate(afterLocal, Destination.SwitchTab(Tab.INTERNET)).newState
        assertEquals(Tab.INTERNET, afterInternet.activeTab)
        assertEquals(DrawerSection.InternetSection.Profile, afterInternet.internetState.activeSection)
        assertEquals(InternetConfig.ProfileRoot, afterInternet.internetState.stack.active)
    }

    // -----------------------------------------------------------------------
    // Domain Test Scenario 5 — retap on root returns NO_OP
    // -----------------------------------------------------------------------

    @Test
    fun `scenario 5 given LOCAL MyQuests backStack empty when onActiveTabRetap LOCAL then returns NO_OP`() {
        val state = defaultState()
        val (_, outcome) = onActiveTabRetap(state, Tab.LOCAL)
        assertEquals(RetapOutcome.NO_OP, outcome)
    }

    @Test
    fun `scenario 5 given LOCAL MyQuests backStack empty when onActiveTabRetap LOCAL then backStack remains empty`() {
        val state = defaultState()
        val (newState, _) = onActiveTabRetap(state, Tab.LOCAL)
        assertTrue(newState.localState.stack.backStack.isEmpty())
    }

    // -----------------------------------------------------------------------
    // Domain Test Scenario 6 — retap with non-empty backStack returns POP_TO_ROOT
    // -----------------------------------------------------------------------

    @Test
    fun `scenario 6 given LOCAL MyQuests with detail in backStack when onActiveTabRetap LOCAL then returns POP_TO_ROOT`() {
        val state = defaultState().copy(
            localState = TabState(
                activeSection = DrawerSection.LocalSection.MyQuests,
                stack = NavStack(
                    active = LocalConfig.MyCoursesRoot, // detail
                    backStack = listOf(LocalConfig.MyQuestsRoot),
                ),
            ),
        )
        val (_, outcome) = onActiveTabRetap(state, Tab.LOCAL)
        assertEquals(RetapOutcome.POP_TO_ROOT, outcome)
    }

    @Test
    fun `scenario 6 given LOCAL MyQuests with detail in backStack when onActiveTabRetap LOCAL then backStack is empty`() {
        val state = defaultState().copy(
            localState = TabState(
                activeSection = DrawerSection.LocalSection.MyQuests,
                stack = NavStack(
                    active = LocalConfig.MyCoursesRoot,
                    backStack = listOf(LocalConfig.MyQuestsRoot),
                ),
            ),
        )
        val (newState, _) = onActiveTabRetap(state, Tab.LOCAL)
        assertTrue(newState.localState.stack.backStack.isEmpty())
    }

    @Test
    fun `scenario 6 given LOCAL with detail in backStack when retap then active becomes root`() {
        val state = defaultState().copy(
            localState = TabState(
                activeSection = DrawerSection.LocalSection.MyQuests,
                stack = NavStack(
                    active = LocalConfig.MyCoursesRoot,
                    backStack = listOf(LocalConfig.MyQuestsRoot),
                ),
            ),
        )
        val (newState, _) = onActiveTabRetap(state, Tab.LOCAL)
        assertEquals(LocalConfig.MyQuestsRoot, newState.localState.stack.active)
    }

    // -----------------------------------------------------------------------
    // Domain Test Scenario 7 — back closes drawer first
    // -----------------------------------------------------------------------

    @Test
    fun `scenario 7 given isDrawerOpen true when back then isDrawerOpen becomes false`() {
        val state = defaultState().copy(isDrawerOpen = true)
        val result = navigate(state, Destination.Back)
        assertFalse(result.newState.isDrawerOpen)
    }

    @Test
    fun `scenario 7 given isDrawerOpen true when back then activeTab does not change`() {
        val state = defaultState().copy(isDrawerOpen = true)
        val result = navigate(state, Destination.Back)
        assertEquals(Tab.LOCAL, result.newState.activeTab)
    }

    @Test
    fun `scenario 7 given isDrawerOpen true when back then childStack does not change`() {
        val state = defaultState().copy(isDrawerOpen = true)
        val result = navigate(state, Destination.Back)
        assertEquals(defaultState().localState.stack, result.newState.localState.stack)
    }

    @Test
    fun `scenario 7 given isDrawerOpen true when back then no events emitted`() {
        val state = defaultState().copy(isDrawerOpen = true)
        val result = navigate(state, Destination.Back)
        assertTrue(result.events.isEmpty())
    }

    // -----------------------------------------------------------------------
    // Domain Test Scenario 8 — back pops stack (backStack not empty)
    // -----------------------------------------------------------------------

    @Test
    fun `scenario 8 given Internet Arena with ArenaDetail in active and ArenaRoot in backStack when back then active becomes ArenaRoot`() {
        val state = defaultState().copy(
            activeTab = Tab.INTERNET,
            internetState = TabState(
                activeSection = DrawerSection.InternetSection.Arena,
                stack = NavStack(
                    active = InternetConfig.CatalogRoot, // simulating ArenaDetail
                    backStack = listOf(InternetConfig.ArenaRoot),
                ),
            ),
            isDrawerOpen = false,
        )
        val result = navigate(state, Destination.Back)
        assertEquals(InternetConfig.ArenaRoot, result.newState.internetState.stack.active)
    }

    @Test
    fun `scenario 8 given Internet with backStack non-empty when back then backStack becomes empty`() {
        val state = defaultState().copy(
            activeTab = Tab.INTERNET,
            internetState = TabState(
                activeSection = DrawerSection.InternetSection.Arena,
                stack = NavStack(
                    active = InternetConfig.CatalogRoot,
                    backStack = listOf(InternetConfig.ArenaRoot),
                ),
            ),
            isDrawerOpen = false,
        )
        val result = navigate(state, Destination.Back)
        assertTrue(result.newState.internetState.stack.backStack.isEmpty())
    }

    // -----------------------------------------------------------------------
    // Domain Test Scenario 9 — back on Internet root switches to LOCAL
    // -----------------------------------------------------------------------

    @Test
    fun `scenario 9 given Internet Arena backStack empty drawer closed when back then activeTab is LOCAL`() {
        val state = defaultState().copy(
            activeTab = Tab.INTERNET,
            isDrawerOpen = false,
        )
        val result = navigate(state, Destination.Back)
        assertEquals(Tab.LOCAL, result.newState.activeTab)
    }

    @Test
    fun `scenario 9 given Internet Arena backStack empty when back then local TabState is restored`() {
        val state = defaultState().copy(activeTab = Tab.INTERNET)
        val result = navigate(state, Destination.Back)
        // Local state should be unchanged (preserved)
        assertEquals(defaultState().localState, result.newState.localState)
    }

    // -----------------------------------------------------------------------
    // Domain Test Scenario 10 — back on LOCAL root emits SystemBack
    // -----------------------------------------------------------------------

    @Test
    fun `scenario 10 given LOCAL MyQuests backStack empty drawer closed when back then SystemBack event emitted`() {
        val state = defaultState()
        val result = navigate(state, Destination.Back)
        assertEquals(listOf(RootEvent.SystemBack), result.events)
    }

    @Test
    fun `scenario 10 given LOCAL MyQuests backStack empty drawer closed when back then state unchanged`() {
        val state = defaultState()
        val result = navigate(state, Destination.Back)
        assertEquals(state, result.newState)
    }

    // -----------------------------------------------------------------------
    // Domain Test Scenario 11 — open drawer on SHOP is no-op
    // -----------------------------------------------------------------------

    @Test
    fun `scenario 11 given activeTab SHOP when openDrawer then isDrawerOpen remains false`() {
        val state = defaultState().copy(activeTab = Tab.SHOP)
        val result = navigate(state, Destination.OpenDrawer)
        assertFalse(result.newState.isDrawerOpen)
    }

    // -----------------------------------------------------------------------
    // Domain Test Scenario 12 — cross-tab section select auto-switches tab
    // -----------------------------------------------------------------------

    @Test
    fun `scenario 12 given LOCAL when selectSection InternetProfile then activeTab is INTERNET`() {
        val state = defaultState()
        val result = navigate(state, Destination.SelectSection(DrawerSection.InternetSection.Profile))
        assertEquals(Tab.INTERNET, result.newState.activeTab)
    }

    @Test
    fun `scenario 12 given LOCAL when selectSection InternetProfile then activeSection is Profile`() {
        val state = defaultState()
        val result = navigate(state, Destination.SelectSection(DrawerSection.InternetSection.Profile))
        assertEquals(DrawerSection.InternetSection.Profile, result.newState.internetState.activeSection)
    }

    @Test
    fun `scenario 12 given LOCAL when selectSection InternetProfile then stack active is ProfileRoot`() {
        val state = defaultState()
        val result = navigate(state, Destination.SelectSection(DrawerSection.InternetSection.Profile))
        assertEquals(InternetConfig.ProfileRoot, result.newState.internetState.stack.active)
    }

    // -----------------------------------------------------------------------
    // Domain Test Scenario 13 — tap on active section closes drawer only
    // -----------------------------------------------------------------------

    @Test
    fun `scenario 13 given Internet Profile drawer open when selectSection Profile then drawer closes`() {
        val state = stateWithInternetProfile().copy(isDrawerOpen = true)
        val result = navigate(state, Destination.SelectSection(DrawerSection.InternetSection.Profile))
        assertFalse(result.newState.isDrawerOpen)
    }

    @Test
    fun `scenario 13 given Internet Profile drawer open when selectSection Profile then activeSection unchanged`() {
        val state = stateWithInternetProfile().copy(isDrawerOpen = true)
        val result = navigate(state, Destination.SelectSection(DrawerSection.InternetSection.Profile))
        assertEquals(DrawerSection.InternetSection.Profile, result.newState.internetState.activeSection)
    }

    @Test
    fun `scenario 13 given Internet Profile drawer open when selectSection Profile then stack unchanged`() {
        val state = stateWithInternetProfile().copy(isDrawerOpen = true)
        val result = navigate(state, Destination.SelectSection(DrawerSection.InternetSection.Profile))
        assertEquals(InternetConfig.ProfileRoot, result.newState.internetState.stack.active)
        assertTrue(result.newState.internetState.stack.backStack.isEmpty())
    }

    // -----------------------------------------------------------------------
    // Domain Test Scenario 15 — back on SHOP root switches to LOCAL
    // -----------------------------------------------------------------------

    @Test
    fun `scenario 15 given SHOP ShopRoot backStack empty drawer closed when back then activeTab is LOCAL`() {
        val state = defaultState().copy(
            activeTab = Tab.SHOP,
            isDrawerOpen = false,
        )
        val result = navigate(state, Destination.Back)
        assertEquals(Tab.LOCAL, result.newState.activeTab)
    }

    // -----------------------------------------------------------------------
    // Domain Test Scenario 17 — open drawer on non-Shop tab
    // -----------------------------------------------------------------------

    @Test
    fun `scenario 17 given LOCAL isDrawerOpen false when openDrawer then isDrawerOpen becomes true`() {
        val state = defaultState()
        val result = navigate(state, Destination.OpenDrawer)
        assertTrue(result.newState.isDrawerOpen)
    }

    @Test
    fun `scenario 17 given LOCAL isDrawerOpen false when openDrawer then activeTab unchanged`() {
        val state = defaultState()
        val result = navigate(state, Destination.OpenDrawer)
        assertEquals(Tab.LOCAL, result.newState.activeTab)
    }

    @Test
    fun `scenario 17 given LOCAL isDrawerOpen false when openDrawer then activeSection unchanged`() {
        val state = defaultState()
        val result = navigate(state, Destination.OpenDrawer)
        assertEquals(DrawerSection.LocalSection.MyQuests, result.newState.localState.activeSection)
    }

    @Test
    fun `scenario 17 given LOCAL isDrawerOpen false when openDrawer then stack unchanged`() {
        val state = defaultState()
        val result = navigate(state, Destination.OpenDrawer)
        assertEquals(defaultState().localState.stack, result.newState.localState.stack)
    }

    // -----------------------------------------------------------------------
    // Domain Test Scenario 18 — close drawer
    // -----------------------------------------------------------------------

    @Test
    fun `scenario 18 given INTERNET isDrawerOpen true when closeDrawer then isDrawerOpen becomes false`() {
        val state = defaultState().copy(activeTab = Tab.INTERNET, isDrawerOpen = true)
        val result = navigate(state, Destination.CloseDrawer)
        assertFalse(result.newState.isDrawerOpen)
    }

    @Test
    fun `scenario 18 given INTERNET isDrawerOpen true when closeDrawer then activeTab unchanged`() {
        val state = defaultState().copy(activeTab = Tab.INTERNET, isDrawerOpen = true)
        val result = navigate(state, Destination.CloseDrawer)
        assertEquals(Tab.INTERNET, result.newState.activeTab)
    }

    @Test
    fun `scenario 18 given INTERNET isDrawerOpen true when closeDrawer then stack unchanged`() {
        val state = defaultState().copy(activeTab = Tab.INTERNET, isDrawerOpen = true)
        val result = navigate(state, Destination.CloseDrawer)
        assertEquals(defaultState().internetState.stack, result.newState.internetState.stack)
    }

    // -----------------------------------------------------------------------
    // Domain Test Scenario 19 — open drawer guard on SHOP (same as 11, but via navigate)
    // -----------------------------------------------------------------------

    @Test
    fun `scenario 19 given SHOP isDrawerOpen false when openDrawer then isDrawerOpen stays false`() {
        val state = defaultState().copy(activeTab = Tab.SHOP)
        val result = navigate(state, Destination.OpenDrawer)
        assertFalse(result.newState.isDrawerOpen)
    }

    // -----------------------------------------------------------------------
    // Domain Test Scenario 20 — tap active section when drawer open (same as 13 variant)
    // -----------------------------------------------------------------------

    @Test
    fun `scenario 20 given Internet Profile activeSection drawer open when selectSection Profile then drawer closes`() {
        val state = stateWithInternetProfile().copy(isDrawerOpen = true)
        val result = navigate(state, Destination.SelectSection(DrawerSection.InternetSection.Profile))
        assertFalse(result.newState.isDrawerOpen)
    }

    @Test
    fun `scenario 20 given Internet Profile activeSection drawer open when selectSection Profile then activeSection unchanged`() {
        val state = stateWithInternetProfile().copy(isDrawerOpen = true)
        val result = navigate(state, Destination.SelectSection(DrawerSection.InternetSection.Profile))
        assertEquals(DrawerSection.InternetSection.Profile, result.newState.internetState.activeSection)
    }

    @Test
    fun `scenario 20 given Internet Profile activeSection drawer open when selectSection Profile then childStack unchanged`() {
        val state = stateWithInternetProfile().copy(isDrawerOpen = true)
        val result = navigate(state, Destination.SelectSection(DrawerSection.InternetSection.Profile))
        assertEquals(InternetConfig.ProfileRoot, result.newState.internetState.stack.active)
        assertTrue(result.newState.internetState.stack.backStack.isEmpty())
    }

    // -----------------------------------------------------------------------
    // Domain Test Scenario 21 — deep link stub (MVP no-op)
    // -----------------------------------------------------------------------

    @Test
    fun `scenario 21 given LOCAL when onDeepLink stub URI then state unchanged`() {
        val state = defaultState()
        val result = onDeepLink(state, DeepLink(uri = "stub://unrecognized"))
        assertEquals(state, result.newState)
    }

    @Test
    fun `scenario 21 given LOCAL when onDeepLink stub URI then no events emitted`() {
        val state = defaultState()
        val result = onDeepLink(state, DeepLink(uri = "stub://unrecognized"))
        assertTrue(result.events.isEmpty())
    }

    // -----------------------------------------------------------------------
    // State Matrix: back-policy FSM — row 1 (drawer open → close)
    // -----------------------------------------------------------------------

    @Test
    fun `back policy row 1 drawerOpen true any tab any stack - closes drawer`() {
        val state = defaultState().copy(isDrawerOpen = true)
        val result = onBack(state)
        assertFalse(result.newState.isDrawerOpen)
        assertTrue(result.events.isEmpty())
    }

    // -----------------------------------------------------------------------
    // State Matrix: back-policy FSM — row 2 (backStack not empty → pop)
    // -----------------------------------------------------------------------

    @Test
    fun `back policy row 2 drawer closed backStack not empty - pops stack`() {
        val state = defaultState().copy(
            localState = TabState(
                activeSection = DrawerSection.LocalSection.MyQuests,
                stack = NavStack(
                    active = LocalConfig.SettingsRoot,
                    backStack = listOf(LocalConfig.MyQuestsRoot),
                ),
            ),
        )
        val result = onBack(state)
        assertEquals(LocalConfig.MyQuestsRoot, result.newState.localState.stack.active)
        assertTrue(result.newState.localState.stack.backStack.isEmpty())
    }

    // -----------------------------------------------------------------------
    // State Matrix: back-policy FSM — row 3 (backStack empty, LOCAL → SystemBack)
    // -----------------------------------------------------------------------

    @Test
    fun `back policy row 3 drawer closed backStack empty activeTab LOCAL - emits SystemBack`() {
        val state = defaultState()
        val result = onBack(state)
        assertTrue(result.events.contains(RootEvent.SystemBack))
    }

    // -----------------------------------------------------------------------
    // State Matrix: back-policy FSM — row 4 (backStack empty, INTERNET → switchTab LOCAL)
    // -----------------------------------------------------------------------

    @Test
    fun `back policy row 4 drawer closed backStack empty activeTab INTERNET - switches to LOCAL`() {
        val state = defaultState().copy(activeTab = Tab.INTERNET)
        val result = onBack(state)
        assertEquals(Tab.LOCAL, result.newState.activeTab)
        assertTrue(result.events.isEmpty())
    }

    // -----------------------------------------------------------------------
    // State Matrix: back-policy FSM — row 5 (backStack empty, EVENTS → switchTab LOCAL)
    // -----------------------------------------------------------------------

    @Test
    fun `back policy row 5 drawer closed backStack empty activeTab EVENTS - switches to LOCAL`() {
        val state = defaultState().copy(activeTab = Tab.EVENTS)
        val result = onBack(state)
        assertEquals(Tab.LOCAL, result.newState.activeTab)
        assertTrue(result.events.isEmpty())
    }

    // -----------------------------------------------------------------------
    // State Matrix: back-policy FSM — row 6 (backStack empty, SHOP → switchTab LOCAL)
    // -----------------------------------------------------------------------

    @Test
    fun `back policy row 6 drawer closed backStack empty activeTab SHOP - switches to LOCAL`() {
        val state = defaultState().copy(activeTab = Tab.SHOP)
        val result = onBack(state)
        assertEquals(Tab.LOCAL, result.newState.activeTab)
        assertTrue(result.events.isEmpty())
    }

    // -----------------------------------------------------------------------
    // State Matrix: re-tap FSM — row 1 (backStack not empty → POP_TO_ROOT)
    // -----------------------------------------------------------------------

    @Test
    fun `retap row 1 backStack not empty - returns POP_TO_ROOT`() {
        val state = defaultState().copy(
            localState = TabState(
                activeSection = DrawerSection.LocalSection.MyQuests,
                stack = NavStack(
                    active = LocalConfig.SettingsRoot,
                    backStack = listOf(LocalConfig.MyQuestsRoot),
                ),
            ),
        )
        val (_, outcome) = onActiveTabRetap(state, Tab.LOCAL)
        assertEquals(RetapOutcome.POP_TO_ROOT, outcome)
    }

    // -----------------------------------------------------------------------
    // State Matrix: re-tap FSM — row 2 (backStack empty → NO_OP)
    // -----------------------------------------------------------------------

    @Test
    fun `retap row 2 backStack empty - returns NO_OP`() {
        val state = defaultState()
        val (_, outcome) = onActiveTabRetap(state, Tab.LOCAL)
        assertEquals(RetapOutcome.NO_OP, outcome)
    }

    // -----------------------------------------------------------------------
    // State Matrix: tab-switch FSM — row 1 (target == current → retap)
    // -----------------------------------------------------------------------

    @Test
    fun `tab switch row 1 target equals current tab - delegates to retap`() {
        val state = defaultState()
        // Same tab, backStack empty → NO_OP (state unchanged)
        val result = navigate(state, Destination.SwitchTab(Tab.LOCAL))
        assertEquals(state.localState, result.newState.localState)
    }

    // -----------------------------------------------------------------------
    // State Matrix: tab-switch FSM — row 2 (target != current → save/restore)
    // -----------------------------------------------------------------------

    @Test
    fun `tab switch row 2 different tab - saves current and activates target`() {
        val state = defaultState()
        val result = navigate(state, Destination.SwitchTab(Tab.INTERNET))
        assertEquals(Tab.INTERNET, result.newState.activeTab)
        // Local state preserved
        assertEquals(defaultState().localState, result.newState.localState)
    }

    // -----------------------------------------------------------------------
    // State Matrix: drawer-section FSM — row 1 (different tab → auto switchTab + setSection)
    // Profile has emptyMap requiredRoles → visible to guest stats
    // -----------------------------------------------------------------------

    @Test
    fun `section switch row 1 section tab differs from activeTab - auto switches tab`() {
        val state = defaultState() // LOCAL, guest stats
        val result = navigate(state, Destination.SelectSection(DrawerSection.InternetSection.Profile))
        assertEquals(Tab.INTERNET, result.newState.activeTab)
    }

    @Test
    fun `section switch row 1 section tab differs from activeTab - sets section`() {
        val state = defaultState() // LOCAL, guest stats
        val result = navigate(state, Destination.SelectSection(DrawerSection.InternetSection.Profile))
        assertEquals(DrawerSection.InternetSection.Profile, result.newState.internetState.activeSection)
    }

    @Test
    fun `section switch row 1 section tab differs from activeTab - closes drawer`() {
        val state = defaultState().copy(isDrawerOpen = true) // guest stats
        val result = navigate(state, Destination.SelectSection(DrawerSection.InternetSection.Profile))
        assertFalse(result.newState.isDrawerOpen)
    }

    @Test
    fun `section switch row 1 section invisible for current stats - no-op guard`() {
        // Arena requires USER >= 3000, guest has 0 → invisible → no-op
        val state = defaultState() // LOCAL, guest stats
        val result = navigate(state, Destination.SelectSection(DrawerSection.InternetSection.Arena))
        assertEquals(state, result.newState)
    }

    @Test
    fun `section switch row 1 section visible with sufficient stats - performs switch`() {
        val state = defaultState().copy(
            userStats = UserStats.guest().copy(currentSkill = 3000),
        )
        val result = navigate(state, Destination.SelectSection(DrawerSection.InternetSection.Arena))
        assertEquals(Tab.INTERNET, result.newState.activeTab)
        assertEquals(DrawerSection.InternetSection.Arena, result.newState.internetState.activeSection)
    }

    // -----------------------------------------------------------------------
    // State Matrix: drawer-section FSM — row 2 (same tab, different section → set section + close)
    // Profile and Qualifications have emptyMap → visible to guest
    // -----------------------------------------------------------------------

    @Test
    fun `section switch row 2 same tab different section - updates activeSection`() {
        // INTERNET, currently showing Arena (saved state), switch to Qualifications (emptyMap = always visible)
        val state = defaultState().copy(activeTab = Tab.INTERNET) // guest stats
        val result = navigate(state, Destination.SelectSection(DrawerSection.InternetSection.Qualifications))
        assertEquals(DrawerSection.InternetSection.Qualifications, result.newState.internetState.activeSection)
    }

    @Test
    fun `section switch row 2 same tab different section - clears stack to root`() {
        val state = defaultState().copy(activeTab = Tab.INTERNET) // guest stats
        val result = navigate(state, Destination.SelectSection(DrawerSection.InternetSection.Qualifications))
        assertEquals(InternetConfig.QualificationsRoot, result.newState.internetState.stack.active)
        assertTrue(result.newState.internetState.stack.backStack.isEmpty())
    }

    @Test
    fun `section switch row 2 same tab different section - closes drawer`() {
        val state = defaultState().copy(activeTab = Tab.INTERNET, isDrawerOpen = true) // guest stats
        val result = navigate(state, Destination.SelectSection(DrawerSection.InternetSection.Qualifications))
        assertFalse(result.newState.isDrawerOpen)
    }

    @Test
    fun `section switch row 2 invisible section - no-op guard`() {
        // INTERNET, try to switch to Catalog (requires USER >= 3000) with guest stats → blocked
        val state = defaultState().copy(activeTab = Tab.INTERNET) // guest stats, currentSkill=0
        val result = navigate(state, Destination.SelectSection(DrawerSection.InternetSection.Catalog))
        assertEquals(state, result.newState)
    }

    // -----------------------------------------------------------------------
    // State Matrix: drawer-section FSM — row 3 (same tab, same section, drawer open → close only)
    // -----------------------------------------------------------------------

    @Test
    fun `section switch row 3 same tab same section drawer open - closes drawer only`() {
        val state = defaultState().copy(isDrawerOpen = true) // LOCAL/MyQuests, drawer open
        val result = navigate(state, Destination.SelectSection(DrawerSection.LocalSection.MyQuests))
        assertFalse(result.newState.isDrawerOpen)
        // Stack unchanged
        assertEquals(defaultState().localState.stack, result.newState.localState.stack)
    }

    // -----------------------------------------------------------------------
    // State Matrix: drawer-section FSM — row 4 (same tab, same section, drawer closed → no-op)
    // -----------------------------------------------------------------------

    @Test
    fun `section switch row 4 same tab same section drawer closed - no-op`() {
        val state = defaultState() // LOCAL/MyQuests, drawer closed
        val result = navigate(state, Destination.SelectSection(DrawerSection.LocalSection.MyQuests))
        assertEquals(state, result.newState)
        assertTrue(result.events.isEmpty())
    }

    // -----------------------------------------------------------------------
    // Domain Test Scenario 34 — SelectSection invisible → no-op (visibility guard)
    // -----------------------------------------------------------------------

    @Test
    fun `scenario 34 given Internet Profile drawer open stats currentSkill 0 when selectSection Arena then state unchanged`() {
        // Arena requires USER >= 3000, guest has currentSkill=0 → invisible → domain no-op
        val state = AppShellState.default().copy(
            activeTab = Tab.INTERNET,
            isDrawerOpen = true,
            internetState = TabState(
                activeSection = DrawerSection.InternetSection.Profile,
                stack = NavStack(active = InternetConfig.ProfileRoot),
            ),
            // userStats = guest (currentSkill = 0) from default()
        )
        val result = navigate(state, Destination.SelectSection(DrawerSection.InternetSection.Arena))
        assertEquals(state, result.newState)
        assertTrue(result.events.isEmpty())
    }

    @Test
    fun `scenario 34 given LOCAL stats currentSkill 0 when cross-tab selectSection Arena then state unchanged`() {
        // Arena is invisible for guest; cross-tab attempt is also blocked
        val state = defaultState() // LOCAL, guest stats
        val result = navigate(state, Destination.SelectSection(DrawerSection.InternetSection.Arena))
        assertEquals(state, result.newState)
    }

    // -----------------------------------------------------------------------
    // Updated tab-switch tests reflecting visibility-aware default state
    // -----------------------------------------------------------------------

    @Test
    fun `tab switch EVENTS with guest stats - eventsState activeSection is null and stack is EmptyRoot`() {
        // FR #7 / BR #3: defaultSection(EVENTS, guestStats) = null (no visible sections for guest)
        // initialEventsTabState → activeSection=null, active=EmptyRoot
        val state = defaultState()
        val result = navigate(state, Destination.SwitchTab(Tab.EVENTS))
        assertEquals(Tab.EVENTS, result.newState.activeTab)
        assertNull(result.newState.eventsState.activeSection)
    }

    // -----------------------------------------------------------------------
    // Domain Test Scenario 41 — OpenDesignCatalog footer action
    // -----------------------------------------------------------------------

    @Test
    fun `scenario 41 given any state when openDesignCatalog then activeTab is LOCAL`() {
        val state = defaultState().copy(activeTab = Tab.INTERNET)
        val result = navigate(state, Destination.OpenDesignCatalog)
        assertEquals(Tab.LOCAL, result.newState.activeTab)
    }

    @Test
    fun `scenario 41 given any state when openDesignCatalog then local activeSection is null`() {
        val state = defaultState()
        val result = navigate(state, Destination.OpenDesignCatalog)
        assertNull(result.newState.localState.activeSection)
    }

    @Test
    fun `scenario 41 given any state when openDesignCatalog then local stack active is DesignCatalogRoot`() {
        val state = defaultState()
        val result = navigate(state, Destination.OpenDesignCatalog)
        assertEquals(LocalConfig.DesignCatalogRoot, result.newState.localState.stack.active)
    }

    @Test
    fun `scenario 41 given any state when openDesignCatalog then backStack is empty`() {
        val state = defaultState()
        val result = navigate(state, Destination.OpenDesignCatalog)
        assertTrue(result.newState.localState.stack.backStack.isEmpty())
    }

    @Test
    fun `scenario 41 given drawer open when openDesignCatalog then isDrawerOpen becomes false`() {
        val state = defaultState().copy(isDrawerOpen = true)
        val result = navigate(state, Destination.OpenDesignCatalog)
        assertFalse(result.newState.isDrawerOpen)
    }

    @Test
    fun `scenario 41 openDesignCatalog preserves other tab states`() {
        // Other tab states must not be touched
        val state = defaultState().copy(
            activeTab = Tab.SHOP,
            internetState = TabState(
                activeSection = DrawerSection.InternetSection.Profile,
                stack = NavStack(active = InternetConfig.ProfileRoot),
            ),
        )
        val result = navigate(state, Destination.OpenDesignCatalog)
        assertEquals(
            DrawerSection.InternetSection.Profile,
            result.newState.internetState.activeSection,
        )
    }
}
