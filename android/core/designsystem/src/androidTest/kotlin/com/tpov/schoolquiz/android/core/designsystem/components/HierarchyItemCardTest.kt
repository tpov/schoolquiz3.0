package com.tpov.schoolquiz.android.core.designsystem.components

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented Compose UI tests for HierarchyItemCard component.
 *
 * Spec traceability: docs/features/quizzes-screen/plan/phase-02/tests.md §HierarchyItemCardTest
 * AC coverage: AC#15, AC#24, AC#25
 * Canonical ref: 04-testing.md §9.2
 */
@RunWith(AndroidJUnit4::class)
class HierarchyItemCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // HI-UI-01
    // GIVEN title="Algebra basics"
    // WHEN rendered
    // THEN text "Algebra basics" is visible
    @Test
    fun title_displayed() {
        composeTestRule.setContent {
            SchoolQuizTheme {
                HierarchyItemCard(
                    title = "Algebra basics",
                    onClick = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Algebra basics").assertIsDisplayed()
    }

    // HI-UI-02
    // GIVEN orderLabel=null
    // THEN no order label text present in composition
    @Test
    fun orderLabel_null_then_not_shown() {
        composeTestRule.setContent {
            SchoolQuizTheme {
                HierarchyItemCard(
                    title = "Topic",
                    orderLabel = null,
                    onClick = {},
                )
            }
        }

        // "1." is the typical order label — must not be present
        assertFalse(
            "Order label '1.' should not appear when orderLabel=null",
            composeTestRule.onAllNodes(hasText("1.")).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    // HI-UI-03 (edge case from tests.md: orderLabel="2." → text "2." visible on left)
    // GIVEN orderLabel="2."
    // WHEN rendered
    // THEN text "2." is visible
    @Test
    fun orderLabel_non_null_then_shown_on_left() {
        composeTestRule.setContent {
            SchoolQuizTheme {
                HierarchyItemCard(
                    title = "Topic",
                    orderLabel = "2.",
                    onClick = {},
                )
            }
        }

        composeTestRule.onNodeWithText("2.").assertIsDisplayed()
    }

    // HI-UI-04
    // GIVEN subtitleCount=null
    // THEN no count text visible
    @Test
    fun subtitleCount_null_then_not_shown() {
        composeTestRule.setContent {
            SchoolQuizTheme {
                HierarchyItemCard(
                    title = "Topic",
                    subtitleCount = null,
                    onClick = {},
                )
            }
        }

        // No subtitle count node should exist
        assertFalse(
            "subtitleCount node should not appear when null",
            composeTestRule.onAllNodes(hasText("5 items")).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    // HI-UI-05
    // GIVEN onClick lambda
    // WHEN performClick
    // THEN lambda called
    @Test
    fun onClick_fires_when_clicked() {
        var clicked = false
        composeTestRule.setContent {
            SchoolQuizTheme {
                HierarchyItemCard(
                    title = "Clickable",
                    onClick = { clicked = true },
                )
            }
        }

        composeTestRule.onNodeWithText("Clickable").performClick()
        assertTrue(clicked)
    }

    // HI-UI-06
    // GIVEN onLongClick=null
    // WHEN performLongClick
    // THEN no onLongClick action fires (callback not invoked)
    @Test
    fun onLongClick_null_then_no_long_press_semantic() {
        composeTestRule.setContent {
            SchoolQuizTheme {
                HierarchyItemCard(
                    title = "NoLongClick",
                    onClick = {},
                    onLongClick = null,
                )
            }
        }

        composeTestRule.onNodeWithText("NoLongClick")
            .assert(SemanticsMatcher("no OnLongClick action") { node ->
                !node.config.contains(SemanticsActions.OnLongClick)
            })
        composeTestRule.onNodeWithText("NoLongClick").performTouchInput { longClick() }
    }

    // HI-UI-07
    // GIVEN onLongClick non-null lambda
    // WHEN performLongClick
    // THEN lambda called
    @Test
    fun onLongClick_non_null_then_fires_on_long_press() {
        var longClicked = false
        composeTestRule.setContent {
            SchoolQuizTheme {
                HierarchyItemCard(
                    title = "WithLongClick",
                    onClick = {},
                    onLongClick = { longClicked = true },
                )
            }
        }

        composeTestRule.onNodeWithText("WithLongClick").performTouchInput { longClick() }
        assertTrue(longClicked)
    }

    // HI-UI-08
    // GIVEN rating=null, ratingCount=null
    // WHEN rendered
    // THEN no rating star icon and no decimal value visible
    @Test
    fun hiUi08_ratingNull_noRatingShown() {
        composeTestRule.setContent {
            SchoolQuizTheme {
                HierarchyItemCard(
                    title = "NoRating",
                    rating = null,
                    ratingCount = null,
                    onClick = {},
                )
            }
        }

        assertFalse(
            "Star icon must not appear when rating=null",
            composeTestRule.onAllNodes(hasText("★", substring = true)).fetchSemanticsNodes().isNotEmpty(),
        )
        assertFalse(
            "Rating decimal must not appear when rating=null",
            composeTestRule.onAllNodes(hasText("4.5", substring = true)).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    // HI-UI-09
    // GIVEN rating=4.5f
    // WHEN rendered
    // THEN text "4.5" is visible
    @Test
    fun hiUi09_ratingNonNull_ratingValueVisible() {
        composeTestRule.setContent {
            SchoolQuizTheme {
                HierarchyItemCard(
                    title = "RatedItem",
                    rating = 4.5f,
                    onClick = {},
                )
            }
        }

        // HierarchyItemCard renders rating as separate Text("★") + Text("4.5") nodes;
        // merged semantics (clickable) surfaces "4.5" via onNodeWithText
        composeTestRule.onNodeWithText("4.5").assertIsDisplayed()
    }

    // HI-UI-10
    // GIVEN rating=4.5f, ratingCount=123
    // WHEN rendered
    // THEN Text("(123)") node is visible
    @Test
    fun hiUi10_ratingCountNonNull_countVisible() {
        composeTestRule.setContent {
            SchoolQuizTheme {
                HierarchyItemCard(
                    title = "RatedWithCount",
                    rating = 4.5f,
                    ratingCount = 123,
                    onClick = {},
                )
            }
        }

        // HierarchyItemCard renders separate Text("(123)") node; merged semantics surfaces it
        composeTestRule.onNodeWithText("(123)").assertIsDisplayed()
    }
}
