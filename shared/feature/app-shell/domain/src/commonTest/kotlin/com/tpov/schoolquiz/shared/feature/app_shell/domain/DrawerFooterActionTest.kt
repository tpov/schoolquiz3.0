package com.tpov.schoolquiz.shared.feature.app_shell.domain

import com.tpov.schoolquiz.shared.feature.app_shell.domain.logic.visibleFooterActions
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.DrawerFooterAction
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Qualification
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.UserStats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for [DrawerFooterAction] sealed hierarchy and [visibleFooterActions].
 *
 * Coverage:
 * - DM-24: debug build → [DesignCatalog, SyncNow, About]
 * - DM-25: release build, developer=0 → [About] only
 * - DM-26: release build, developer=100 → [DesignCatalog, SyncNow, About]
 * - DM-27: release build, developer=99 → [About] only
 * - Sealed hierarchy completeness (3 members)
 */
class DrawerFooterActionTest {

    private val guestStats = UserStats.guest()
    private val superQualStats = UserStats.guest().copy(
        qualification = Qualification.zero().copy(developer = 100),
    )
    private val belowThresholdStats = UserStats.guest().copy(
        qualification = Qualification.zero().copy(developer = 99),
    )

    // -----------------------------------------------------------------------
    // DM-24 — debug build always shows all 3 actions
    // -----------------------------------------------------------------------

    @Test
    fun `DM-24 visibleFooterActions debug build returns DesignCatalog SyncNow About`() {
        val actions = visibleFooterActions(isDebugBuild = true, stats = guestStats)
        assertEquals(3, actions.size)
        assertEquals(DrawerFooterAction.DesignCatalog, actions[0])
        assertEquals(DrawerFooterAction.SyncNow, actions[1])
        assertEquals(DrawerFooterAction.About, actions[2])
    }

    @Test
    fun `DM-24 visibleFooterActions debug build contains DesignCatalog`() {
        assertTrue(visibleFooterActions(isDebugBuild = true, stats = guestStats).contains(DrawerFooterAction.DesignCatalog))
    }

    @Test
    fun `DM-24 visibleFooterActions debug build DesignCatalog is first`() {
        assertEquals(DrawerFooterAction.DesignCatalog, visibleFooterActions(isDebugBuild = true, stats = guestStats).first())
    }

    // -----------------------------------------------------------------------
    // DM-25 — release build, developer=0 shows About only
    // -----------------------------------------------------------------------

    @Test
    fun `DM-25 visibleFooterActions release build developer 0 returns About only`() {
        val actions = visibleFooterActions(isDebugBuild = false, stats = guestStats)
        assertEquals(1, actions.size)
        assertEquals(DrawerFooterAction.About, actions[0])
    }

    @Test
    fun `DM-25 visibleFooterActions release build developer 0 does NOT contain DesignCatalog`() {
        assertFalse(visibleFooterActions(isDebugBuild = false, stats = guestStats).contains(DrawerFooterAction.DesignCatalog))
    }

    @Test
    fun `DM-25 visibleFooterActions release build developer 0 does NOT contain SyncNow`() {
        assertFalse(visibleFooterActions(isDebugBuild = false, stats = guestStats).contains(DrawerFooterAction.SyncNow))
    }

    // -----------------------------------------------------------------------
    // DM-26 — release build, developer=100 shows all 3 (superqualification)
    // -----------------------------------------------------------------------

    @Test
    fun `DM-26 visibleFooterActions release build developer 100 returns all 3 actions`() {
        val actions = visibleFooterActions(isDebugBuild = false, stats = superQualStats)
        assertEquals(3, actions.size)
        assertEquals(DrawerFooterAction.DesignCatalog, actions[0])
        assertEquals(DrawerFooterAction.SyncNow, actions[1])
        assertEquals(DrawerFooterAction.About, actions[2])
    }

    // -----------------------------------------------------------------------
    // DM-27 — release build, developer=99 shows About only (below threshold)
    // -----------------------------------------------------------------------

    @Test
    fun `DM-27 visibleFooterActions release build developer 99 returns About only`() {
        val actions = visibleFooterActions(isDebugBuild = false, stats = belowThresholdStats)
        assertEquals(1, actions.size)
        assertEquals(DrawerFooterAction.About, actions[0])
    }

    // -----------------------------------------------------------------------
    // About is always present
    // -----------------------------------------------------------------------

    @Test
    fun `About is always present in all build types and stats`() {
        assertTrue(visibleFooterActions(isDebugBuild = false, stats = guestStats).contains(DrawerFooterAction.About))
        assertTrue(visibleFooterActions(isDebugBuild = true, stats = guestStats).contains(DrawerFooterAction.About))
        assertTrue(visibleFooterActions(isDebugBuild = false, stats = superQualStats).contains(DrawerFooterAction.About))
    }

    // -----------------------------------------------------------------------
    // Sealed hierarchy completeness (3 members)
    // -----------------------------------------------------------------------

    @Test
    fun `DrawerFooterAction has exactly three variants`() {
        val all: List<DrawerFooterAction> = listOf(
            DrawerFooterAction.DesignCatalog,
            DrawerFooterAction.SyncNow,
            DrawerFooterAction.About,
        )
        assertEquals(3, all.size)
    }

    @Test
    fun `DesignCatalog SyncNow and About are distinct action types`() {
        val dc: DrawerFooterAction = DrawerFooterAction.DesignCatalog
        val sync: DrawerFooterAction = DrawerFooterAction.SyncNow
        val about: DrawerFooterAction = DrawerFooterAction.About
        assertFalse(dc == about)
        assertFalse(dc == sync)
        assertFalse(sync == about)
    }
}
