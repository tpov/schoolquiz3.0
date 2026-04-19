package com.tpov.schoolquiz.shared.feature.app_shell.domain

import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.LocalConfig
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.RetapOutcome
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Tab
import com.tpov.schoolquiz.shared.feature.app_shell.domain.state.AppShellState
import com.tpov.schoolquiz.shared.feature.app_shell.domain.use_case.OnTabRetapUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for [OnTabRetapUseCase].
 *
 * Coverage:
 * - Re-tap FSM rows 1-2 via use case (Domain Test Scenarios 5, 6)
 * - Journey 5 (POP_TO_ROOT) and Journey 6 (NO_OP) via use case
 */
class OnTabRetapUseCaseTest {

    private val useCase = OnTabRetapUseCase()

    private fun defaultState(): AppShellState = AppShellState.default()

    // -----------------------------------------------------------------------
    // Row 2: backStack empty → NO_OP (Scenario 5)
    // -----------------------------------------------------------------------

    @Test
    fun `scenario 5 retap LOCAL with empty backStack returns NO_OP`() {
        val state = defaultState() // backStack=[]
        val (_, outcome) = useCase(state, Tab.LOCAL)
        assertEquals(RetapOutcome.NO_OP, outcome)
    }

    @Test
    fun `scenario 5 retap NO_OP does not change state`() {
        val state = defaultState()
        val (newState, _) = useCase(state, Tab.LOCAL)
        assertEquals(state, newState)
    }

    // -----------------------------------------------------------------------
    // Row 1: backStack not empty → POP_TO_ROOT (Scenario 6)
    // -----------------------------------------------------------------------

    @Test
    fun `scenario 6 retap LOCAL with non-empty backStack returns POP_TO_ROOT`() {
        val stateWithStack = defaultState().copy(
            localState = defaultState().localState.copy(
                stack = defaultState().localState.stack.push(LocalConfig.MyCoursesRoot),
            ),
        )
        val (_, outcome) = useCase(stateWithStack, Tab.LOCAL)
        assertEquals(RetapOutcome.POP_TO_ROOT, outcome)
    }

    @Test
    fun `scenario 6 retap POP_TO_ROOT clears backStack`() {
        val stateWithStack = defaultState().copy(
            localState = defaultState().localState.copy(
                stack = defaultState().localState.stack.push(LocalConfig.MyCoursesRoot),
            ),
        )
        val (newState, _) = useCase(stateWithStack, Tab.LOCAL)
        assertTrue(newState.localState.stack.isAtRoot)
    }

    @Test
    fun `scenario 6 retap POP_TO_ROOT sets active to root config`() {
        // Push MyCoursesRoot on top, so root (bottom) is MyQuestsRoot
        val stateWithStack = defaultState().copy(
            localState = defaultState().localState.copy(
                stack = defaultState().localState.stack.push(LocalConfig.MyCoursesRoot),
            ),
        )
        val (newState, _) = useCase(stateWithStack, Tab.LOCAL)
        assertEquals(LocalConfig.MyQuestsRoot, newState.localState.stack.active)
    }

    // -----------------------------------------------------------------------
    // Journey 5 — full scenario
    // -----------------------------------------------------------------------

    @Test
    fun `journey 5 retap with deep stack clears to root`() {
        // active=Detail2, backStack=[Root, Detail]
        val detail2Stack = defaultState().localState.stack
            .push(LocalConfig.MyCoursesRoot)
            .push(LocalConfig.SettingsRoot)
        val stateWithDeepStack = defaultState().copy(
            localState = defaultState().localState.copy(stack = detail2Stack),
        )

        val (newState, outcome) = useCase(stateWithDeepStack, Tab.LOCAL)

        assertEquals(RetapOutcome.POP_TO_ROOT, outcome)
        assertTrue(newState.localState.stack.isAtRoot)
        assertEquals(LocalConfig.MyQuestsRoot, newState.localState.stack.active)
    }
}
