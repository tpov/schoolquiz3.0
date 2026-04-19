package com.tpov.schoolquiz.android.core.designsystem

import org.junit.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * JVM invariant tests for Phase-03 Brand Component wrappers.
 *
 * Spec traceability (docs/features/app-shell-menu/plan/phase-03/frontend.md, Pattern Invariants):
 *  - no_hardcoded_colors_in_components (invariant #1: all colors via MaterialTheme.colorScheme.*)
 *  - brand_progress_bar_uses_coerce_in (invariant: clamping via coerceIn, not manual if-checks)
 *  - all_wrappers_have_preview (invariant #3: each public composable has @Preview)
 */
class BrandComponentsInvariantsTest {

    private val componentsSourceRoot = File("src/main/kotlin/com/tpov/schoolquiz/android/core/designsystem/components")

    // Spec invariant #1: no_hardcoded_colors_in_components
    // GIVEN components/ sources WHEN searching for Color(0xFF...) THEN absent
    @Test
    fun `no hardcoded Color literals in brand component wrappers`() {
        if (!componentsSourceRoot.exists()) return // skip if run outside Gradle module context

        val violations = componentsSourceRoot.walkTopDown()
            .filter { it.extension == "kt" }
            .filter { "Color(0x" in it.readText() }
            .map { it.name }
            .toList()

        assertFalse(
            violations.isNotEmpty(),
            "Hardcoded Color literals found in components: $violations — use MaterialTheme.colorScheme.* (spec Pattern Invariant #1)"
        )
    }

    // Spec invariant: brand_progress_bar_uses_coerce_in
    // GIVEN BrandProgressBar.kt source WHEN checking clamping logic THEN coerceIn present
    @Test
    fun `BrandProgressBar source uses coerceIn for progress clamping`() {
        val progressBarFile = File(componentsSourceRoot, "BrandProgressBar.kt")
        if (!progressBarFile.exists()) return // skip if run outside Gradle module context

        assertTrue(
            progressBarFile.readText().contains("coerceIn"),
            "BrandProgressBar.kt must use coerceIn for progress clamping — prevents rendering artifacts at progress > 1f or < 0f"
        )
    }

    // Spec invariant #3: all_wrappers_have_preview
    // GIVEN components/ sources WHEN checking for @Preview annotations THEN each .kt has @Preview
    @Test
    fun `all brand wrapper files have at least one Preview annotation`() {
        if (!componentsSourceRoot.exists()) return // skip if run outside Gradle module context

        val missingPreview = componentsSourceRoot.walkTopDown()
            .filter { it.extension == "kt" }
            .filter { "@Preview" !in it.readText() }
            .map { it.name }
            .toList()

        assertFalse(
            missingPreview.isNotEmpty(),
            "Missing @Preview in: $missingPreview — all public composables must have @Preview per spec Pattern Invariant #3"
        )
    }
}
