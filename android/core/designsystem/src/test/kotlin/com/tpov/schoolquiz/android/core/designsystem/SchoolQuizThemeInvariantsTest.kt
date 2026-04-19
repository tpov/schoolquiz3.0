package com.tpov.schoolquiz.android.core.designsystem

import org.junit.Test
import java.io.File
import kotlin.test.assertFalse

/**
 * JVM invariant tests for SchoolQuizTheme source structure.
 *
 * Spec traceability (docs/features/app-shell-menu/plan/phase-02/overview.md):
 *  - no_light_theme_exported
 */
class SchoolQuizThemeInvariantsTest {

    // Spec scenario: no_light_theme_exported
    // GIVEN source file Color.kt WHEN searching for lightColorScheme THEN absent (ADR-0010 dark-only)
    @Test
    fun `no lightColorScheme in designsystem sources`() {
        val sourceRoot = File("src/main/kotlin")
        if (!sourceRoot.exists()) return // skip if run outside Gradle module context

        val violations = sourceRoot.walkTopDown()
            .filter { it.extension == "kt" }
            .filter { it.readText().contains("lightColorScheme") }
            .map { it.relativeTo(sourceRoot).path }
            .toList()

        assertFalse(violations.isNotEmpty(), "lightColorScheme found in: $violations — dark-only per ADR-0010 (spec NFR #4)")
    }
}
