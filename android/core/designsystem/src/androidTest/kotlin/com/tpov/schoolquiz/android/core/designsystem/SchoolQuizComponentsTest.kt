package com.tpov.schoolquiz.android.core.designsystem

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tpov.schoolquiz.android.core.designsystem.catalog.DesignCatalogScreen
import com.tpov.schoolquiz.android.core.designsystem.components.BrandCard
import com.tpov.schoolquiz.android.core.designsystem.components.BrandCircleIconButton
import com.tpov.schoolquiz.android.core.designsystem.components.BrandPrimaryButton
import com.tpov.schoolquiz.android.core.designsystem.components.BrandProgressBar
import com.tpov.schoolquiz.android.core.designsystem.components.BrandSecondaryButton
import com.tpov.schoolquiz.android.core.designsystem.components.CategoryIcon
import junit.framework.TestCase.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented smoke tests for Phase-03 Brand Component wrappers + DesignCatalogScreen.
 *
 * Spec traceability (docs/features/app-shell-menu/plan/phase-03/overview.md):
 *  - brand_card_renders_without_crash
 *  - brand_primary_button_click
 *  - brand_progress_bar_value_range
 *  - brand_progress_bar_clamps_value_above_1 (edge case)
 *  - design_catalog_screen_renders
 * Scope: 6 wrappers + DesignCatalogScreen smoke coverage per tests.md.
 */
@RunWith(AndroidJUnit4::class)
class SchoolQuizComponentsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // Spec scenario: brand_card_renders_without_crash
    // GIVEN BrandCard { Text("card content") } WHEN composed in SchoolQuizTheme THEN content visible
    @Test
    fun brand_card_renders_content() {
        composeTestRule.setContent {
            SchoolQuizTheme {
                BrandCard { Text("card content", Modifier.testTag("card_content")) }
            }
        }
        composeTestRule.onNodeWithTag("card_content").assertIsDisplayed()
    }

    // Spec scenario: brand_primary_button_click
    // GIVEN BrandPrimaryButton(onClick = { clicked = true }) WHEN button clicked THEN clicked == true
    @Test
    fun brand_primary_button_triggers_click() {
        var clicked = false
        composeTestRule.setContent {
            SchoolQuizTheme {
                BrandPrimaryButton(text = "Test", onClick = { clicked = true })
            }
        }
        composeTestRule.onNodeWithText("Test").performClick()
        assertTrue(clicked)
    }

    // Spec scenario: brand_progress_bar_value_range
    // GIVEN BrandProgressBar(progress = 0.5f) WHEN composed THEN renders without crash
    @Test
    fun brand_progress_bar_renders_for_valid_progress() {
        composeTestRule.setContent {
            SchoolQuizTheme { BrandProgressBar(progress = 0.5f, Modifier.testTag("pb")) }
        }
        composeTestRule.onNodeWithTag("pb").assertIsDisplayed()
    }

    // Spec scenario: brand_progress_bar_clamps_value_above_1
    // GIVEN BrandProgressBar(progress = 2.0f) WHEN composed THEN no crash (coerceIn clamps to 1f)
    @Test
    fun brand_progress_bar_clamps_value_above_1() {
        composeTestRule.setContent {
            SchoolQuizTheme { BrandProgressBar(progress = 2.0f) }
        }
    }

    // Spec scenario: brand_progress_bar_clamps_value_below_0 (edge case, lower bound)
    // GIVEN BrandProgressBar(progress = -0.5f) WHEN composed THEN no crash (coerceIn clamps to 0f)
    @Test
    fun brand_progress_bar_clamps_value_below_0() {
        composeTestRule.setContent {
            SchoolQuizTheme { BrandProgressBar(progress = -0.5f) }
        }
    }

    // Spec scope coverage: 6 wrappers smoke — BrandSecondaryButton
    // GIVEN BrandSecondaryButton WHEN composed in SchoolQuizTheme THEN renders without crash
    @Test
    fun brand_secondary_button_renders_without_crash() {
        composeTestRule.setContent {
            SchoolQuizTheme {
                BrandSecondaryButton(text = "Secondary", onClick = {})
            }
        }
        composeTestRule.onNodeWithText("Secondary").assertIsDisplayed()
    }

    // Spec scope coverage: 6 wrappers smoke — BrandCircleIconButton
    // GIVEN BrandCircleIconButton WHEN composed in SchoolQuizTheme THEN renders without crash
    @Test
    fun brand_circle_icon_button_renders_without_crash() {
        composeTestRule.setContent {
            SchoolQuizTheme {
                BrandCircleIconButton(
                    icon = Icons.Default.Star,
                    contentDescription = "star",
                    onClick = {},
                    modifier = Modifier.testTag("circle_btn"),
                )
            }
        }
        composeTestRule.onNodeWithTag("circle_btn").assertIsDisplayed()
    }

    // Spec scope coverage: 6 wrappers smoke — CategoryIcon
    // GIVEN CategoryIcon WHEN composed in SchoolQuizTheme THEN renders without crash
    @Test
    fun category_icon_renders_without_crash() {
        composeTestRule.setContent {
            SchoolQuizTheme {
                CategoryIcon(
                    icon = Icons.Default.Star,
                    contentDescription = "test",
                    modifier = Modifier.testTag("cat_icon"),
                )
            }
        }
        composeTestRule.onNodeWithTag("cat_icon").assertIsDisplayed()
    }

    // Spec scenario: design_catalog_screen_renders
    // GIVEN DesignCatalogScreen() in SchoolQuizTheme WHEN composed THEN section titles visible
    @Test
    fun design_catalog_screen_renders_all_section_titles() {
        composeTestRule.setContent {
            SchoolQuizTheme { DesignCatalogScreen() }
        }
        composeTestRule.onNodeWithText("Design Catalog").assertIsDisplayed()
        composeTestRule.onNodeWithText("BrandCard").assertIsDisplayed()
        composeTestRule.onNodeWithText("BrandPrimaryButton").assertIsDisplayed()
    }
}
