package com.tpov.schoolquiz.android.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Instrumented tests for SchoolQuizTheme.
 * Runner: AndroidJUnitRunner (configured globally in AndroidLibraryConventionPlugin).
 *
 * Spec traceability (docs/features/app-shell-menu/plan/phase-02/overview.md):
 *  - theme_dark_colors_match_adr_0010
 *  - theme_colors_primary_secondary
 *  - theme_shapes_small_medium_large
 */
class SchoolQuizThemeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // Spec scenario: theme_dark_colors_match_adr_0010
    // GIVEN SchoolQuizTheme applied WHEN colorScheme.background accessed THEN Color(0xFF000000)
    @Test
    fun theme_background_is_pure_black() {
        var background: Color = Color.Transparent
        composeTestRule.setContent {
            SchoolQuizTheme {
                background = MaterialTheme.colorScheme.background
            }
        }
        assertEquals(Color(0xFF000000), background)
    }

    // Spec scenario: theme_colors_primary_secondary
    // GIVEN SchoolQuizTheme applied WHEN colorScheme.primary accessed THEN Color(0xFF4285F4)
    @Test
    fun theme_primary_is_google_blue() {
        var primary: Color = Color.Transparent
        composeTestRule.setContent {
            SchoolQuizTheme {
                primary = MaterialTheme.colorScheme.primary
            }
        }
        assertEquals(Color(0xFF4285F4), primary)
    }

    // Spec scenario: theme_colors_primary_secondary
    // GIVEN SchoolQuizTheme applied WHEN colorScheme.secondary accessed THEN Color(0xFFFFD700)
    @Test
    fun theme_secondary_is_gold() {
        var secondary: Color = Color.Transparent
        composeTestRule.setContent {
            SchoolQuizTheme {
                secondary = MaterialTheme.colorScheme.secondary
            }
        }
        assertEquals(Color(0xFFFFD700), secondary)
    }

    // Spec scenario: theme_shapes_small_medium_large
    // GIVEN SchoolQuizTheme applied WHEN shapes accessed THEN correct dp per 06-api-contract.md:392-394
    // extraSmall=4dp, small=8dp, medium=12dp, large=16dp, extraLarge=24dp
    @Test
    fun theme_shapes_match_api_contract() {
        var smallShape: RoundedCornerShape? = null
        var mediumShape: RoundedCornerShape? = null
        var largeShape: RoundedCornerShape? = null
        composeTestRule.setContent {
            SchoolQuizTheme {
                smallShape = MaterialTheme.shapes.small as? RoundedCornerShape
                mediumShape = MaterialTheme.shapes.medium as? RoundedCornerShape
                largeShape = MaterialTheme.shapes.large as? RoundedCornerShape
            }
        }
        assertNotNull(smallShape, "small shape was null — theme may not have been applied")
        assertNotNull(mediumShape, "medium shape was null — theme may not have been applied")
        assertNotNull(largeShape, "large shape was null — theme may not have been applied")
        assertEquals(RoundedCornerShape(8.dp), smallShape)
        assertEquals(RoundedCornerShape(12.dp), mediumShape)
        assertEquals(RoundedCornerShape(16.dp), largeShape)
    }
}
