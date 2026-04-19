package com.tpov.schoolquiz.shared.feature.app_shell.domain

import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.LocalConfig
import com.tpov.schoolquiz.shared.feature.app_shell.domain.state.NavStack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for NavStack data structure.
 * NavStack is the pure domain replacement for Decompose ChildStack.
 */
class NavStackTest {

    @Test
    fun `new stack isAtRoot returns true`() {
        val stack: NavStack<LocalConfig> = NavStack(active = LocalConfig.MyQuestsRoot)
        assertTrue(stack.isAtRoot)
    }

    @Test
    fun `push increments backStack and sets new active`() {
        val stack: NavStack<LocalConfig> = NavStack(active = LocalConfig.MyQuestsRoot)
        val pushed = stack.push(LocalConfig.MyCoursesRoot)
        assertEquals(LocalConfig.MyCoursesRoot, pushed.active)
        assertEquals(listOf<LocalConfig>(LocalConfig.MyQuestsRoot), pushed.backStack)
    }

    @Test
    fun `push sets isAtRoot to false`() {
        val stack: NavStack<LocalConfig> = NavStack(active = LocalConfig.MyQuestsRoot)
        val pushed = stack.push(LocalConfig.MyCoursesRoot)
        assertFalse(pushed.isAtRoot)
    }

    @Test
    fun `pop on root returns null`() {
        val stack: NavStack<LocalConfig> = NavStack(active = LocalConfig.MyQuestsRoot)
        assertNull(stack.pop())
    }

    @Test
    fun `pop after push returns original root`() {
        val stack: NavStack<LocalConfig> = NavStack(active = LocalConfig.MyQuestsRoot)
        val pushed = stack.push(LocalConfig.MyCoursesRoot)
        val popped = pushed.pop()!!
        assertEquals(LocalConfig.MyQuestsRoot, popped.active)
        assertTrue(popped.backStack.isEmpty())
    }

    @Test
    fun `replaceWithRoot clears backStack`() {
        val stack: NavStack<LocalConfig> = NavStack(active = LocalConfig.MyCoursesRoot)
        val pushed = stack.push(LocalConfig.SettingsRoot)
        val replaced = pushed.replaceWithRoot(LocalConfig.MyQuestsRoot)
        assertEquals(LocalConfig.MyQuestsRoot, replaced.active)
        assertTrue(replaced.backStack.isEmpty())
    }

    @Test
    fun `multiple pushes build correct backStack order`() {
        val stack: NavStack<LocalConfig> = NavStack(active = LocalConfig.MyQuestsRoot)
        val pushed = stack.push(LocalConfig.MyCoursesRoot).push(LocalConfig.SettingsRoot)

        assertEquals(LocalConfig.SettingsRoot, pushed.active)
        assertEquals(listOf<LocalConfig>(LocalConfig.MyQuestsRoot, LocalConfig.MyCoursesRoot), pushed.backStack)
    }

    @Test
    fun `pop from depth-2 stack returns correct state`() {
        val stack: NavStack<LocalConfig> = NavStack(active = LocalConfig.MyQuestsRoot)
        val deep = stack.push(LocalConfig.MyCoursesRoot).push(LocalConfig.SettingsRoot)

        val popped = deep.pop()!!
        assertEquals(LocalConfig.MyCoursesRoot, popped.active)
        assertEquals(listOf<LocalConfig>(LocalConfig.MyQuestsRoot), popped.backStack)
    }
}
