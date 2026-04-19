package com.tpov.schoolquiz.shared.feature.app_shell.domain

import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.DrawerSection
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.InternetConfig
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.RootEvent
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Tab
import com.tpov.schoolquiz.shared.feature.app_shell.domain.state.AppShellState
import com.tpov.schoolquiz.shared.feature.app_shell.domain.state.NavStack
import com.tpov.schoolquiz.shared.feature.app_shell.domain.state.TabState
import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.HandleBackUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for [HandleBackUseCase].
 *
 * Coverage:
 * - Back-policy FSM rows 1-6 via use case (Journeys 4, 10, 13)
 * - Domain Test Scenarios 7, 8, 9, 10, 15 via use case
 */
class HandleBackUseCaseTest {

    private val useCase = HandleBackUseCase()

    private fun defaultState(): AppShellState = AppShellState.default()

    // -----------------------------------------------------------------------
    // Row 1: drawer open → close drawer
    // -----------------------------------------------------------------------

    @Test
    fun `back row 1 drawer open closes drawer and preserves tab`() {
        val state = defaultState().copy(isDrawerOpen = true)
        val result = useCase(state)
        assertFalse(result.newState.isDrawerOpen)
        assertEquals(Tab.LOCAL, result.newState.activeTab)
        assertTrue(result.events.isEmpty())
    }

    // -----------------------------------------------------------------------
    // Row 2: backStack not empty → pop
    // -----------------------------------------------------------------------

    @Test
    fun `back row 2 backStack not empty pops stack`() {
        val stateWithStack = defaultState().copy(
            activeTab = Tab.INTERNET,
            internetState = TabState(
                activeSection = DrawerSection.InternetSection.Arena,
                stack = NavStack<InternetConfig>(
                    active = InternetConfig.ArenaRoot,
                    backStack = listOf(),
                ).push(InternetConfig.CatalogRoot),
            ),
        )
        val result = useCase(stateWithStack)
        assertTrue(result.newState.internetState.stack.isAtRoot)
        assertEquals(Tab.INTERNET, result.newState.activeTab)
        assertTrue(result.events.isEmpty())
    }

    // -----------------------------------------------------------------------
    // Row 3: backStack empty + LOCAL → SystemBack
    // -----------------------------------------------------------------------

    @Test
    fun `back row 3 LOCAL root backStack empty emits SystemBack`() {
        val state = defaultState() // activeTab=LOCAL, backStack=[]
        val result = useCase(state)
        assertTrue(result.events.contains(RootEvent.SystemBack))
        assertEquals(state, result.newState)
    }

    // -----------------------------------------------------------------------
    // Rows 4-6: backStack empty + non-LOCAL → switch to LOCAL
    // -----------------------------------------------------------------------

    @Test
    fun `back row 4 INTERNET root backStack empty switches to LOCAL`() {
        val state = defaultState().copy(activeTab = Tab.INTERNET)
        val result = useCase(state)
        assertEquals(Tab.LOCAL, result.newState.activeTab)
        assertTrue(result.events.isEmpty())
    }

    @Test
    fun `back row 5 EVENTS root backStack empty switches to LOCAL`() {
        val state = defaultState().copy(activeTab = Tab.EVENTS)
        val result = useCase(state)
        assertEquals(Tab.LOCAL, result.newState.activeTab)
    }

    @Test
    fun `back row 6 SHOP root backStack empty switches to LOCAL`() {
        val state = defaultState().copy(activeTab = Tab.SHOP)
        val result = useCase(state)
        assertEquals(Tab.LOCAL, result.newState.activeTab)
    }

    // -----------------------------------------------------------------------
    // Journey 4 — back sequence in full
    // -----------------------------------------------------------------------

    @Test
    fun `journey 4 back sequence three steps ends with SystemBack event`() {
        // Setup: Internet/Profile with detail in backStack
        val step0 = defaultState().copy(
            activeTab = Tab.INTERNET,
            internetState = TabState(
                activeSection = DrawerSection.InternetSection.Profile,
                stack = NavStack<InternetConfig>(
                    active = InternetConfig.ProfileRoot,
                    backStack = listOf(),
                ).push(InternetConfig.CatalogRoot),
            ),
        )

        // Step 1: pop backStack
        val step1 = useCase(step0)
        assertEquals(Tab.INTERNET, step1.newState.activeTab)
        assertTrue(step1.newState.internetState.stack.isAtRoot)

        // Step 2: switch to LOCAL
        val step2 = useCase(step1.newState)
        assertEquals(Tab.LOCAL, step2.newState.activeTab)

        // Step 3: emit SystemBack from LOCAL root
        val step3 = useCase(step2.newState)
        assertTrue(step3.events.contains(RootEvent.SystemBack))
    }
}
