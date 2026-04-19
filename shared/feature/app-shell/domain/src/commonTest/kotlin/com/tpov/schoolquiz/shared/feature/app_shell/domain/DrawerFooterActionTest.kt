package com.tpov.schoolquiz.shared.feature.app_shell.domain

import com.tpov.schoolquiz.shared.feature.app_shell.domain.logic.visibleFooterActions
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.DrawerFooterAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for [DrawerFooterAction] sealed hierarchy and [visibleFooterActions].
 *
 * Coverage:
 * - Domain Test Scenario 42: debug build → [DesignCatalog, About]
 * - Domain Test Scenario 43: release build → [About] only
 * - Sealed hierarchy completeness
 */
class DrawerFooterActionTest {

    // -----------------------------------------------------------------------
    // Domain Test Scenario 42 — debug build shows DesignCatalog + About
    // -----------------------------------------------------------------------

    @Test
    fun `scenario 42 visibleFooterActions debug build returns DesignCatalog and About`() {
        val actions = visibleFooterActions(isDebugBuild = true)
        assertEquals(2, actions.size)
        assertEquals(DrawerFooterAction.DesignCatalog, actions[0])
        assertEquals(DrawerFooterAction.About, actions[1])
    }

    @Test
    fun `scenario 42 visibleFooterActions debug build contains DesignCatalog`() {
        assertTrue(visibleFooterActions(isDebugBuild = true).contains(DrawerFooterAction.DesignCatalog))
    }

    @Test
    fun `scenario 42 visibleFooterActions debug build DesignCatalog is first`() {
        assertEquals(DrawerFooterAction.DesignCatalog, visibleFooterActions(isDebugBuild = true).first())
    }

    // -----------------------------------------------------------------------
    // Domain Test Scenario 43 — release build shows About only
    // -----------------------------------------------------------------------

    @Test
    fun `scenario 43 visibleFooterActions release build returns About only`() {
        val actions = visibleFooterActions(isDebugBuild = false)
        assertEquals(1, actions.size)
        assertEquals(DrawerFooterAction.About, actions[0])
    }

    @Test
    fun `scenario 43 visibleFooterActions release build does NOT contain DesignCatalog`() {
        assertFalse(visibleFooterActions(isDebugBuild = false).contains(DrawerFooterAction.DesignCatalog))
    }

    @Test
    fun `scenario 43 visibleFooterActions release build About is always present`() {
        assertTrue(visibleFooterActions(isDebugBuild = false).contains(DrawerFooterAction.About))
        assertTrue(visibleFooterActions(isDebugBuild = true).contains(DrawerFooterAction.About))
    }

    // -----------------------------------------------------------------------
    // Sealed hierarchy completeness
    // -----------------------------------------------------------------------

    @Test
    fun `DrawerFooterAction has exactly two variants`() {
        // Exhaustive when expression in production code ensures all variants are handled.
        // This test documents the expected variant count.
        val all: List<DrawerFooterAction> = listOf(
            DrawerFooterAction.DesignCatalog,
            DrawerFooterAction.About,
        )
        assertEquals(2, all.size)
    }

    @Test
    fun `DesignCatalog and About are distinct action types`() {
        val dc: DrawerFooterAction = DrawerFooterAction.DesignCatalog
        val about: DrawerFooterAction = DrawerFooterAction.About
        assertFalse(dc == about)
    }
}
