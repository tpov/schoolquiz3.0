package com.tpov.schoolquiz.shared.feature.app_shell.domain

import com.tpov.schoolquiz.shared.feature.app_shell.domain.logic.fallbackState
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.DrawerSection
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.EventsConfig
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.InternetConfig
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.LocalConfig
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.ShopConfig
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Tab
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.UserStats
import com.tpov.schoolquiz.shared.feature.app_shell.domain.state.AppShellState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for AppShellState default/fallback factories and basic invariants.
 *
 * Covers:
 * - Domain Test Scenario 1 (cold start / default state)
 * - Domain Test Scenario 16 (corrupted SavedState fallback)
 * - Drawer visibility FSM (all 4 rows)
 */
class AppShellStateTest {

    // -----------------------------------------------------------------------
    // Domain Test Scenario 1 — cold start default state
    // -----------------------------------------------------------------------

    @Test
    fun `given cold start when default state created then activeTab is LOCAL`() {
        val state = AppShellState.default()
        assertEquals(Tab.LOCAL, state.activeTab)
    }

    @Test
    fun `given cold start when default state created then local activeSection is HomeQuests`() {
        val state = AppShellState.default()
        assertEquals(DrawerSection.LocalSection.HomeQuests, state.localState.activeSection)
    }

    @Test
    fun `given cold start when default state created then local stack active is HomeQuestsRoot`() {
        val state = AppShellState.default()
        assertEquals(LocalConfig.HomeQuestsRoot, state.localState.stack.active)
    }

    @Test
    fun `given cold start when default state created then local backStack is empty`() {
        val state = AppShellState.default()
        assertTrue(state.localState.stack.backStack.isEmpty())
    }

    @Test
    fun `given cold start when default state created then isDrawerOpen is false`() {
        val state = AppShellState.default()
        assertFalse(state.isDrawerOpen)
    }

    @Test
    fun `given cold start with guest stats when default state created then internet activeSection is Profile`() {
        // FR #7 / BR #3: defaultSection = visibleSections(INTERNET, guestStats).first()
        // Guest has skill=0; Arena/Catalog/Social/Leaderboard require skill >= 3000/10000
        // First visible for guest: Profile (emptyMap)
        val state = AppShellState.default()
        assertEquals(DrawerSection.InternetSection.Profile, state.internetState.activeSection)
    }

    @Test
    fun `given cold start with guest stats when default state created then events activeSection is QualifierTournament`() {
        // Qualifier is public, so EVENTS has a visible root for ordinary users.
        val state = AppShellState.default()
        assertEquals(DrawerSection.EventsSection.QualifierTournament, state.eventsState.activeSection)
    }

    @Test
    fun `given cold start with guest stats when default state created then events stack active is QualifierTournamentRoot`() {
        val state = AppShellState.default()
        assertEquals(EventsConfig.QualifierTournamentRoot, state.eventsState.stack.active)
    }

    @Test
    fun `given cold start when default state created then shop activeSection is null`() {
        val state = AppShellState.default()
        assertNull(state.shopState.activeSection)
    }

    @Test
    fun `given cold start when default state created then shop stack active is ShopRoot`() {
        val state = AppShellState.default()
        assertEquals(ShopConfig.ShopRoot, state.shopState.stack.active)
    }

    // -----------------------------------------------------------------------
    // Domain Test Scenario 16 — corrupted SavedState fallback
    // -----------------------------------------------------------------------

    @Test
    fun `given corrupted saved state when fallback called then activeTab is LOCAL`() {
        val state = fallbackState()
        assertEquals(Tab.LOCAL, state.activeTab)
    }

    @Test
    fun `given corrupted saved state when fallback called then local activeSection is HomeQuests`() {
        val state = fallbackState()
        assertEquals(DrawerSection.LocalSection.HomeQuests, state.localState.activeSection)
    }

    @Test
    fun `given corrupted saved state when fallback called then each tab stack has empty backStack`() {
        val state = fallbackState()
        assertTrue(state.localState.stack.backStack.isEmpty())
        assertTrue(state.internetState.stack.backStack.isEmpty())
        assertTrue(state.eventsState.stack.backStack.isEmpty())
        assertTrue(state.shopState.stack.backStack.isEmpty())
    }

    @Test
    fun `given corrupted saved state when fallback called then internet stack active is ProfileRoot`() {
        // Fallback uses same formula: guest stats → Profile is first visible for INTERNET
        val state = fallbackState()
        assertEquals(InternetConfig.ProfileRoot, state.internetState.stack.active)
    }

    @Test
    fun `given corrupted saved state when fallback called then events stack active is QualifierTournamentRoot`() {
        // Fallback uses formula: guest stats → Qualifier is first visible for EVENTS.
        val state = fallbackState()
        assertEquals(EventsConfig.QualifierTournamentRoot, state.eventsState.stack.active)
    }

    @Test
    fun `given corrupted saved state when fallback called then shop stack active is ShopRoot`() {
        val state = fallbackState()
        assertEquals(ShopConfig.ShopRoot, state.shopState.stack.active)
    }

    @Test
    fun `given corrupted saved state when fallback called then isDrawerOpen is false`() {
        val state = fallbackState()
        assertFalse(state.isDrawerOpen)
    }

    // -----------------------------------------------------------------------
    // Drawer visibility FSM — 4 rows
    // -----------------------------------------------------------------------

    @Test
    fun `drawer visibility row 1 LOCAL tab - isShopActive is false`() {
        val state = AppShellState.default().copy(activeTab = Tab.LOCAL)
        assertFalse(state.isShopActive)
    }

    @Test
    fun `drawer visibility row 2 INTERNET tab - isShopActive is false`() {
        val state = AppShellState.default().copy(activeTab = Tab.INTERNET)
        assertFalse(state.isShopActive)
    }

    @Test
    fun `drawer visibility row 3 EVENTS tab - isShopActive is false`() {
        val state = AppShellState.default().copy(activeTab = Tab.EVENTS)
        assertFalse(state.isShopActive)
    }

    @Test
    fun `drawer visibility row 4 SHOP tab - isShopActive is true`() {
        val state = AppShellState.default().copy(activeTab = Tab.SHOP)
        assertTrue(state.isShopActive)
    }

    // -----------------------------------------------------------------------
    // activeSection derived property
    // -----------------------------------------------------------------------

    @Test
    fun `activeSection returns null when activeTab is SHOP`() {
        val state = AppShellState.default().copy(activeTab = Tab.SHOP)
        assertNull(state.activeSection)
    }

    @Test
    fun `activeSection returns local section when activeTab is LOCAL`() {
        val state = AppShellState.default()
        assertEquals(DrawerSection.LocalSection.HomeQuests, state.activeSection)
    }

    // -----------------------------------------------------------------------
    // Domain Test Scenario 36 — initialTabState formula for INTERNET guest
    // -----------------------------------------------------------------------

    @Test
    fun `scenario 36 initialTabState INTERNET guest stats activeSection is Profile`() {
        // FR #7 / BR #3: defaultSection(INTERNET, guest) = visibleSections.first() = Profile
        val state = AppShellState.default()
        assertEquals(DrawerSection.InternetSection.Profile, state.internetState.activeSection)
        assertEquals(InternetConfig.ProfileRoot, state.internetState.stack.active)
    }

    @Test
    fun `scenario 36 initialTabState INTERNET skill 3000 activeSection is Arena`() {
        // At skill >= 3000: Arena is first visible for INTERNET
        val stats = UserStats.guest().copy(currentSkill = 3_000)
        val state = AppShellState.default(stats)
        assertEquals(DrawerSection.InternetSection.Arena, state.internetState.activeSection)
        assertEquals(InternetConfig.ArenaRoot, state.internetState.stack.active)
    }

    // -----------------------------------------------------------------------
    // Domain Test Scenario 37 — public qualifier for EVENTS with guest stats
    // -----------------------------------------------------------------------

    @Test
    fun `scenario 37 initialTabState EVENTS guest stats activeSection QualifierTournament`() {
        val state = AppShellState.default()
        assertEquals(DrawerSection.EventsSection.QualifierTournament, state.eventsState.activeSection)
        assertEquals(EventsConfig.QualifierTournamentRoot, state.eventsState.stack.active)
    }

    @Test
    fun `scenario 37 initialTabState EVENTS skill 10000 activeSection remains QualifierTournament`() {
        // At skill >= 10000: Minigames becomes visible, but Qualifier remains first.
        val stats = UserStats.guest().copy(currentSkill = 10_000)
        val state = AppShellState.default(stats)
        assertEquals(DrawerSection.EventsSection.QualifierTournament, state.eventsState.activeSection)
        assertEquals(EventsConfig.QualifierTournamentRoot, state.eventsState.stack.active)
    }

    // -----------------------------------------------------------------------
    // Domain Test Scenario 38 — public qualifier switch: user switches to EVENTS tab when guest
    // -----------------------------------------------------------------------

    @Test
    fun `scenario 38 default eventsState with guest stats has QualifierTournamentRoot backStack empty`() {
        val state = AppShellState.default()
        // Public qualifier root is valid — backStack must be empty
        assertTrue(state.eventsState.stack.backStack.isEmpty())
        assertEquals(EventsConfig.QualifierTournamentRoot, state.eventsState.stack.active)
    }

    // -----------------------------------------------------------------------
    // Domain Test Scenario 39 — fallback uses same formula as default
    // -----------------------------------------------------------------------

    @Test
    fun `scenario 39 fallback and default produce equal states for same stats`() {
        val fallback = fallbackState()
        val default = AppShellState.default()
        assertEquals(default, fallback)
    }
}
