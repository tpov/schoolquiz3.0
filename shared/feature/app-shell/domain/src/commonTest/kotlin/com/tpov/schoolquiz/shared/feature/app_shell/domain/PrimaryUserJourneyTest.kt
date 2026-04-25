package com.tpov.schoolquiz.shared.feature.app_shell.domain

import com.tpov.schoolquiz.shared.feature.app_shell.domain.logic.fallbackState
import com.tpov.schoolquiz.shared.feature.app_shell.domain.logic.navigate
import com.tpov.schoolquiz.shared.feature.app_shell.domain.logic.onActiveTabRetap
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Destination
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.DrawerSection
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.EventsConfig
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.InternetConfig
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.LocalConfig
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.RetapOutcome
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.RootEvent
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.ShopConfig
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Tab
import com.tpov.schoolquiz.shared.feature.app_shell.domain.state.AppShellState
import com.tpov.schoolquiz.shared.feature.app_shell.domain.state.NavStack
import com.tpov.schoolquiz.shared.feature.app_shell.domain.state.TabState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration-style tests for Primary User Journeys.
 * Each test walks a full journey through the domain FSM using pure function chains.
 *
 * Coverage: Journey 1, Journey 3, Journey 4, Journey 5, Journey 12, Journey 13, Journey 14, Journey 15
 */
class PrimaryUserJourneyTest {

    // -----------------------------------------------------------------------
    // Journey 1 — Cold start (Happy Path)
    // -----------------------------------------------------------------------

    @Test
    fun `journey 1 cold start default state matches spec`() {
        val state = AppShellState.default()

        // activeTab = LOCAL
        assertEquals(Tab.LOCAL, state.activeTab)
        // Local activeSection = HomeQuests (first in visibleSections after Phase 02 reorder)
        assertEquals(DrawerSection.LocalSection.HomeQuests, state.localState.activeSection)
        // local childStack.active = HomeQuestsRoot
        assertEquals(LocalConfig.HomeQuestsRoot, state.localState.stack.active)
        // backStack empty
        assertTrue(state.localState.stack.backStack.isEmpty())
        // drawer closed
        assertFalse(state.isDrawerOpen)
    }

    // -----------------------------------------------------------------------
    // Journey 3 — Tab switching with state preservation (Happy Path)
    // -----------------------------------------------------------------------

    @Test
    fun `journey 3 internet profile switch to shop switch back to internet state preserved`() {
        // Start: INTERNET/Profile (explicitly constructed — Profile has emptyMap, always visible)
        val start = AppShellState.default().copy(
            activeTab = Tab.INTERNET,
            internetState = TabState(
                activeSection = DrawerSection.InternetSection.Profile,
                stack = NavStack(active = InternetConfig.ProfileRoot),
            ),
        )
        assertEquals(DrawerSection.InternetSection.Profile, start.internetState.activeSection)

        // Step 1: go to SHOP
        val afterShop = navigate(start, Destination.SwitchTab(Tab.SHOP)).newState
        assertEquals(Tab.SHOP, afterShop.activeTab)
        // Internet state preserved (BR #8)
        assertEquals(DrawerSection.InternetSection.Profile, afterShop.internetState.activeSection)

        // Step 2: go back to INTERNET
        val afterInternet = navigate(afterShop, Destination.SwitchTab(Tab.INTERNET)).newState
        assertEquals(Tab.INTERNET, afterInternet.activeTab)
        // Internet state restored (BR #8)
        assertEquals(DrawerSection.InternetSection.Profile, afterInternet.internetState.activeSection)
        assertEquals(InternetConfig.ProfileRoot, afterInternet.internetState.stack.active)
    }

    // -----------------------------------------------------------------------
    // Journey 4 — Back sequence (Recovery Path)
    // -----------------------------------------------------------------------

    @Test
    fun `journey 4 back sequence pops stack then switches tab then emits SystemBack`() {
        // Start: Internet/Profile, active=ProfileDetail, backStack=[ProfileRoot]
        val start = AppShellState.default().copy(
            activeTab = Tab.INTERNET,
            internetState = TabState(
                activeSection = DrawerSection.InternetSection.Profile,
                stack = NavStack(
                    active = InternetConfig.CatalogRoot, // simulating ProfileDetail
                    backStack = listOf(InternetConfig.ProfileRoot),
                ),
            ),
        )

        // Step 1: back pops stack
        val after1 = navigate(start, Destination.Back)
        assertEquals(Tab.INTERNET, after1.newState.activeTab)
        assertEquals(InternetConfig.ProfileRoot, after1.newState.internetState.stack.active)
        assertTrue(after1.newState.internetState.stack.backStack.isEmpty())
        assertTrue(after1.events.isEmpty())

        // Step 2: back on root of Internet → switch to LOCAL
        val after2 = navigate(after1.newState, Destination.Back)
        assertEquals(Tab.LOCAL, after2.newState.activeTab)
        // Internet TabState preserved (section = Profile)
        assertEquals(DrawerSection.InternetSection.Profile, after2.newState.internetState.activeSection)
        assertTrue(after2.events.isEmpty())

        // Step 3: back on LOCAL root → emit SystemBack
        val after3 = navigate(after2.newState, Destination.Back)
        assertEquals(Tab.LOCAL, after3.newState.activeTab)
        assertTrue(after3.events.contains(RootEvent.SystemBack))
    }

    // -----------------------------------------------------------------------
    // Journey 5 — Re-tap active tab with backStack (Edge Path)
    // -----------------------------------------------------------------------

    @Test
    fun `journey 5 retap active tab with non-empty backStack pops to root`() {
        // Start: LOCAL/MyQuests, backStack has detail screens
        val start = AppShellState.default().copy(
            localState = TabState(
                activeSection = DrawerSection.LocalSection.MyQuests,
                stack = NavStack(
                    active = LocalConfig.SettingsRoot, // simulating MyQuestsDetail2
                    backStack = listOf(LocalConfig.MyQuestsRoot, LocalConfig.HomeQuestsRoot),
                ),
            ),
        )

        val (newState, outcome) = onActiveTabRetap(start, Tab.LOCAL)
        assertEquals(RetapOutcome.POP_TO_ROOT, outcome)
        assertEquals(LocalConfig.MyQuestsRoot, newState.localState.stack.active)
        assertTrue(newState.localState.stack.backStack.isEmpty())
    }

    // -----------------------------------------------------------------------
    // Journey 12 — Tap on active drawer section (Edge Path)
    // -----------------------------------------------------------------------

    @Test
    fun `journey 12 tap active section closes drawer only - no state change`() {
        // Start: Internet/Profile, drawer open
        val start = AppShellState.default().copy(
            activeTab = Tab.INTERNET,
            internetState = TabState(
                activeSection = DrawerSection.InternetSection.Profile,
                stack = NavStack(active = InternetConfig.ProfileRoot),
            ),
            isDrawerOpen = true,
        )

        val result = navigate(start, Destination.SelectSection(DrawerSection.InternetSection.Profile))
        assertFalse(result.newState.isDrawerOpen)
        assertEquals(DrawerSection.InternetSection.Profile, result.newState.internetState.activeSection)
        assertEquals(InternetConfig.ProfileRoot, result.newState.internetState.stack.active)
    }

    // -----------------------------------------------------------------------
    // Journey 13 — Back on root Shop switches to LOCAL (Edge Path)
    // -----------------------------------------------------------------------

    @Test
    fun `journey 13 back on Shop root switches to LOCAL`() {
        val start = AppShellState.default().copy(
            activeTab = Tab.SHOP,
            isDrawerOpen = false,
        )

        val result = navigate(start, Destination.Back)
        assertEquals(Tab.LOCAL, result.newState.activeTab)
        // Local state restored
        assertEquals(DrawerSection.LocalSection.HomeQuests, result.newState.localState.activeSection)
    }

    // -----------------------------------------------------------------------
    // Journey 14 — Corrupted saved state fallback (Recovery Path)
    // -----------------------------------------------------------------------

    @Test
    fun `journey 14 corrupted state fallback produces valid default state`() {
        val state = fallbackState()

        assertEquals(Tab.LOCAL, state.activeTab)
        assertEquals(DrawerSection.LocalSection.HomeQuests, state.localState.activeSection)
        assertEquals(LocalConfig.HomeQuestsRoot, state.localState.stack.active)
        assertTrue(state.localState.stack.backStack.isEmpty())
        assertFalse(state.isDrawerOpen)

        // Internet: guest stats → first visible = Qualifications
        assertEquals(InternetConfig.QualificationsRoot, state.internetState.stack.active)
        // Events: guest stats → no visible sections → EmptyRoot
        assertEquals(EventsConfig.EmptyRoot, state.eventsState.stack.active)
        assertNull(state.eventsState.activeSection)
        // Shop: always ShopRoot
        assertEquals(ShopConfig.ShopRoot, state.shopState.stack.active)
    }

    // -----------------------------------------------------------------------
    // Journey 15 — Cross-tab deep link section (Edge Path)
    // -----------------------------------------------------------------------

    @Test
    fun `journey 15 cross-tab selectSection from LOCAL to Internet Profile`() {
        val start = AppShellState.default() // LOCAL/MyQuests

        val result = navigate(start, Destination.SelectSection(DrawerSection.InternetSection.Profile))

        assertEquals(Tab.INTERNET, result.newState.activeTab)
        assertEquals(DrawerSection.InternetSection.Profile, result.newState.internetState.activeSection)
        assertEquals(InternetConfig.ProfileRoot, result.newState.internetState.stack.active)
        assertFalse(result.newState.isDrawerOpen)
    }
}
