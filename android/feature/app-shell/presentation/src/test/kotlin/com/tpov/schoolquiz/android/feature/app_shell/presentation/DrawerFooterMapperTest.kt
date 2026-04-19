package com.tpov.schoolquiz.android.feature.app_shell.presentation

import com.tpov.schoolquiz.shared.feature.app_shell.domain.logic.visibleFooterActions
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.DrawerFooterAction
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * JVM tests for DrawerFooter visibility mapper (domain logic: visibleFooterActions).
 * Spec: Domain Test Scenarios 42-43.
 */
class DrawerFooterMapperTest {

    @Test
    fun `drawer_footer_mapper_debug_shows_design_catalog`() {
        val actions = visibleFooterActions(isDebugBuild = true)
        assertTrue(
            actual = actions.contains(DrawerFooterAction.DesignCatalog),
            message = "Debug build must expose DesignCatalog footer action",
        )
    }

    @Test
    fun `drawer_footer_mapper_release_hides_design_catalog`() {
        val actions = visibleFooterActions(isDebugBuild = false)
        assertFalse(
            actual = actions.contains(DrawerFooterAction.DesignCatalog),
            message = "Release build must NOT expose DesignCatalog footer action",
        )
    }

    @Test
    fun `drawer_footer_mapper_always_shows_about`() {
        assertTrue(visibleFooterActions(isDebugBuild = true).contains(DrawerFooterAction.About))
        assertTrue(visibleFooterActions(isDebugBuild = false).contains(DrawerFooterAction.About))
    }
}
