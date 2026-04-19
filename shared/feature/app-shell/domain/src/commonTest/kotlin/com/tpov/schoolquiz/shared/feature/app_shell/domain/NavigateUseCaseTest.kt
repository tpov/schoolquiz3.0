package com.tpov.schoolquiz.shared.feature.app_shell.domain

import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Destination
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.DrawerSection
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.InternetConfig
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.RootEvent
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Tab
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.UserStats
import com.tpov.schoolquiz.shared.feature.app_shell.domain.state.AppShellState
import com.tpov.schoolquiz.shared.feature.app_shell.domain.state.NavStack
import com.tpov.schoolquiz.shared.feature.app_shell.domain.state.TabState
import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.NavigateUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for [NavigateUseCase] — verifies delegation to pure navigate() function.
 *
 * Coverage:
 * - Domain Test Scenarios 2-4 (tab switch + section switch + preservation) through use case
 * - Domain Test Scenarios 7-10 (back policy) through use case
 * - Domain Test Scenario 12 (cross-tab section) through use case
 * - Domain Test Scenario 15 (shop back) through use case
 * - Domain Test Scenario 17 (open drawer) through use case
 * - Domain Test Scenario 19 (shop guard) through use case
 * - Domain Test Scenario 34 (hidden section guard) through use case
 */
class NavigateUseCaseTest {

    private val useCase = NavigateUseCase()

    private fun defaultState(): AppShellState = AppShellState.default()

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
    // Scenario 2 — tab switch INTERNET, local state preserved
    // -----------------------------------------------------------------------

    @Test
    fun `scenario 2 switch to INTERNET tab succeeds`() {
        val state = defaultState()
        val result = useCase(state, Destination.SwitchTab(Tab.INTERNET))
        assertEquals(Tab.INTERNET, result.newState.activeTab)
    }

    @Test
    fun `scenario 2 local state preserved after switching to INTERNET`() {
        val state = defaultState()
        val result = useCase(state, Destination.SwitchTab(Tab.INTERNET))
        assertEquals(DrawerSection.LocalSection.MyQuests, result.newState.localState.activeSection)
    }

    // -----------------------------------------------------------------------
    // Scenario 3 — select internet section Profile (guest-visible)
    // -----------------------------------------------------------------------

    @Test
    fun `scenario 3 select Profile section changes activeSection`() {
        val state = defaultState().copy(activeTab = Tab.INTERNET)
        val result = useCase(state, Destination.SelectSection(DrawerSection.InternetSection.Profile))
        assertEquals(DrawerSection.InternetSection.Profile, result.newState.internetState.activeSection)
    }

    @Test
    fun `scenario 3 select Profile section closes drawer and clears backStack`() {
        val state = defaultState().copy(activeTab = Tab.INTERNET, isDrawerOpen = true)
        val result = useCase(state, Destination.SelectSection(DrawerSection.InternetSection.Profile))
        assertFalse(result.newState.isDrawerOpen)
        assertEquals(emptyList(), result.newState.internetState.stack.backStack)
    }

    // -----------------------------------------------------------------------
    // Scenario 4 — tab switch preserves internet profile state
    // -----------------------------------------------------------------------

    @Test
    fun `scenario 4 internet Profile preserved after switch LOCAL and back`() {
        val internetProfile = stateWithInternetProfile()
        val afterLocal = useCase(internetProfile, Destination.SwitchTab(Tab.LOCAL))
        val afterBackToInternet = useCase(afterLocal.newState, Destination.SwitchTab(Tab.INTERNET))
        assertEquals(DrawerSection.InternetSection.Profile, afterBackToInternet.newState.internetState.activeSection)
        assertEquals(InternetConfig.ProfileRoot, afterBackToInternet.newState.internetState.stack.active)
    }

    // -----------------------------------------------------------------------
    // Scenario 7 — back closes drawer
    // -----------------------------------------------------------------------

    @Test
    fun `scenario 7 back when drawer open closes drawer only`() {
        val state = defaultState().copy(isDrawerOpen = true)
        val result = useCase(state, Destination.Back)
        assertFalse(result.newState.isDrawerOpen)
        assertEquals(defaultState().activeTab, result.newState.activeTab)
    }

    // -----------------------------------------------------------------------
    // Scenario 8 — back pops backStack
    // -----------------------------------------------------------------------

    @Test
    fun `scenario 8 back when backStack not empty pops stack`() {
        // depth-1 stack: active=CatalogRoot, backStack=[ArenaRoot]
        val stateWithStack = defaultState().copy(
            activeTab = Tab.INTERNET,
            internetState = TabState(
                activeSection = DrawerSection.InternetSection.Arena,
                stack = NavStack<InternetConfig>(
                    active = InternetConfig.ArenaRoot,
                ).push(InternetConfig.CatalogRoot),
            ),
        )
        val result = useCase(stateWithStack, Destination.Back)
        assertTrue(result.newState.internetState.stack.isAtRoot)
    }

    // -----------------------------------------------------------------------
    // Scenario 9 — back on root INTERNET switches to LOCAL
    // -----------------------------------------------------------------------

    @Test
    fun `scenario 9 back on INTERNET root switches to LOCAL`() {
        val state = defaultState().copy(activeTab = Tab.INTERNET)
        val result = useCase(state, Destination.Back)
        assertEquals(Tab.LOCAL, result.newState.activeTab)
    }

    // -----------------------------------------------------------------------
    // Scenario 10 — back on LOCAL root emits SystemBack event
    // -----------------------------------------------------------------------

    @Test
    fun `scenario 10 back on LOCAL root emits SystemBack event`() {
        val state = defaultState() // activeTab=LOCAL, backStack=[]
        val result = useCase(state, Destination.Back)
        assertTrue(result.events.contains(RootEvent.SystemBack))
    }

    // -----------------------------------------------------------------------
    // Scenario 12 — cross-tab section select (deep link scenario)
    // -----------------------------------------------------------------------

    @Test
    fun `scenario 12 select InternetSection Profile from LOCAL switches tab and section`() {
        val state = defaultState() // activeTab = LOCAL
        val result = useCase(state, Destination.SelectSection(DrawerSection.InternetSection.Profile))
        assertEquals(Tab.INTERNET, result.newState.activeTab)
        assertEquals(DrawerSection.InternetSection.Profile, result.newState.internetState.activeSection)
    }

    // -----------------------------------------------------------------------
    // Scenario 15 — Shop back switches to LOCAL
    // -----------------------------------------------------------------------

    @Test
    fun `scenario 15 back on SHOP switches to LOCAL`() {
        val state = defaultState().copy(activeTab = Tab.SHOP)
        val result = useCase(state, Destination.Back)
        assertEquals(Tab.LOCAL, result.newState.activeTab)
    }

    // -----------------------------------------------------------------------
    // Scenario 17 — open drawer
    // -----------------------------------------------------------------------

    @Test
    fun `scenario 17 open drawer when LOCAL makes isDrawerOpen true`() {
        val state = defaultState()
        val result = useCase(state, Destination.OpenDrawer)
        assertTrue(result.newState.isDrawerOpen)
    }

    // -----------------------------------------------------------------------
    // Scenario 19 — open drawer on SHOP is no-op guard
    // -----------------------------------------------------------------------

    @Test
    fun `scenario 19 open drawer on SHOP is no-op`() {
        val state = defaultState().copy(activeTab = Tab.SHOP)
        val result = useCase(state, Destination.OpenDrawer)
        assertFalse(result.newState.isDrawerOpen)
    }

    // -----------------------------------------------------------------------
    // Scenario 34 — hidden section guard (deep link to invisible section)
    // -----------------------------------------------------------------------

    @Test
    fun `scenario 34 select Arena when guest stats invisible returns no-op state`() {
        // guest.currentSkill = 0, Arena requires USER >= 3000 → invisible
        val state = stateWithInternetProfile() // Internet/Profile, guest stats
        val result = useCase(state, Destination.SelectSection(DrawerSection.InternetSection.Arena))
        // state should be unchanged — visibility guard prevents navigation
        assertEquals(DrawerSection.InternetSection.Profile, result.newState.internetState.activeSection)
        assertEquals(state.activeTab, result.newState.activeTab)
    }

    // -----------------------------------------------------------------------
    // Close drawer
    // -----------------------------------------------------------------------

    @Test
    fun `close drawer when open changes isDrawerOpen to false`() {
        val state = defaultState().copy(isDrawerOpen = true)
        val result = useCase(state, Destination.CloseDrawer)
        assertFalse(result.newState.isDrawerOpen)
    }

    @Test
    fun `close drawer when already closed is no-op`() {
        val state = defaultState() // drawer closed
        val result = useCase(state, Destination.CloseDrawer)
        assertFalse(result.newState.isDrawerOpen)
    }
}
