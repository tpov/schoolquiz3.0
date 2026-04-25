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
 * Phase-07 traceability tests for [visibleFooterActions] render condition.
 *
 * Covers DC-phase7-01..04 from docs/features/menu-refactor/plan/phase-07/tests.md.
 * Spec: DesignCatalog / SyncNow visibility matrix (isDebugBuild × developer level).
 *
 * Note: underlying logic is also covered by DrawerFooterActionTest (DM-24..27) and
 * VisibilityTest. These tests exist as phase-07 specific traceability anchors per tests.md.
 */
class VisibilityDesignCatalogTest {

    private fun statsWithDeveloper(level: Int): UserStats = UserStats.guest().copy(
        qualification = Qualification.zero().copy(developer = level),
    )

    // -----------------------------------------------------------------------
    // DC-phase7-01
    // GIVEN isDebugBuild=false, stats=UserStats.guest() (developer=0)
    // WHEN visibleFooterActions called
    // THEN result == [DrawerFooterAction.About] — no DesignCatalog, no SyncNow
    // -----------------------------------------------------------------------
    @Test
    fun `DC-phase7-01 release build developer 0 returns About only`() {
        val result = visibleFooterActions(isDebugBuild = false, stats = UserStats.guest())

        assertEquals(listOf(DrawerFooterAction.About), result)
        assertFalse(result.contains(DrawerFooterAction.DesignCatalog))
        assertFalse(result.contains(DrawerFooterAction.SyncNow))
    }

    // -----------------------------------------------------------------------
    // DC-phase7-02
    // GIVEN isDebugBuild=false, stats.qualification.developer=100 (>= LEVEL_1.points)
    // WHEN visibleFooterActions called
    // THEN result contains DesignCatalog and SyncNow
    // -----------------------------------------------------------------------
    @Test
    fun `DC-phase7-02 release build developer 100 contains DesignCatalog and SyncNow`() {
        val result = visibleFooterActions(isDebugBuild = false, stats = statsWithDeveloper(100))

        assertTrue(result.contains(DrawerFooterAction.DesignCatalog))
        assertTrue(result.contains(DrawerFooterAction.SyncNow))
        assertTrue(result.contains(DrawerFooterAction.About))
        assertEquals(3, result.size)
    }

    // -----------------------------------------------------------------------
    // DC-phase7-03
    // GIVEN isDebugBuild=true, stats=UserStats.guest() (developer=0, debug OR-bypass)
    // WHEN visibleFooterActions called
    // THEN result contains DesignCatalog and SyncNow (debug build bypasses developer check)
    // -----------------------------------------------------------------------
    @Test
    fun `DC-phase7-03 debug build developer 0 contains DesignCatalog and SyncNow via debug bypass`() {
        val result = visibleFooterActions(isDebugBuild = true, stats = UserStats.guest())

        assertTrue(result.contains(DrawerFooterAction.DesignCatalog))
        assertTrue(result.contains(DrawerFooterAction.SyncNow))
        assertEquals(3, result.size)
    }

    // -----------------------------------------------------------------------
    // DC-phase7-04
    // GIVEN isDebugBuild=false, stats.qualification.developer=99 (below LEVEL_1.points=100)
    // WHEN visibleFooterActions called
    // THEN result == [DrawerFooterAction.About] — developer 99 is below activation threshold
    // -----------------------------------------------------------------------
    @Test
    fun `DC-phase7-04 release build developer 99 below threshold returns About only`() {
        val result = visibleFooterActions(isDebugBuild = false, stats = statsWithDeveloper(99))

        assertEquals(listOf(DrawerFooterAction.About), result)
        assertFalse(result.contains(DrawerFooterAction.DesignCatalog))
        assertFalse(result.contains(DrawerFooterAction.SyncNow))
    }
}
