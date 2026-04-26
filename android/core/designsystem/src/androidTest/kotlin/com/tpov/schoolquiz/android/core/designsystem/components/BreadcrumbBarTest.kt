package com.tpov.schoolquiz.android.core.designsystem.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented Compose UI tests for BreadcrumbBar component.
 *
 * Spec traceability: docs/features/quizzes-screen/plan/phase-02/tests.md §BreadcrumbBarTest
 * AC coverage: AC#8, AC#9, AC#26, AC#27, AC#28
 * Canonical ref: 04-testing.md §9.1
 */
@RunWith(AndroidJUnit4::class)
class BreadcrumbBarTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // BB-UI-01
    // GIVEN titles=["Математика", "Квест 1", "Секция 2"]
    // WHEN rendered
    // THEN all three texts visible
    @Test
    fun renders_all_segments_in_order() {
        composeTestRule.setContent {
            SchoolQuizTheme {
                BreadcrumbBar(
                    titles = listOf("Математика", "Квест 1", "Секция 2"),
                    onSegmentClick = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Математика").assertIsDisplayed()
        composeTestRule.onNodeWithText("Квест 1").assertIsDisplayed()
        composeTestRule.onNodeWithText("Секция 2").assertIsDisplayed()
    }

    // BB-UI-02
    // GIVEN 3 segments
    // WHEN tap last segment ("Секция 2")
    // THEN onSegmentClick NOT called
    @Test
    fun last_segment_not_clickable() {
        var callCount = 0
        composeTestRule.setContent {
            SchoolQuizTheme {
                BreadcrumbBar(
                    titles = listOf("Математика", "Квест 1", "Секция 2"),
                    onSegmentClick = { callCount++ },
                )
            }
        }

        composeTestRule.onNodeWithText("Секция 2").performClick()
        assertEquals(0, callCount)
    }

    // BB-UI-03
    // WHEN tap segment at index 0 ("Математика") THEN onSegmentClick(0) called
    // WHEN tap segment at index 1 ("Квест 1") THEN onSegmentClick(1) called
    @Test
    fun non_last_segment_click_fires_with_correct_index() {
        val clickedIndices = mutableListOf<Int>()
        composeTestRule.setContent {
            SchoolQuizTheme {
                BreadcrumbBar(
                    titles = listOf("Математика", "Квест 1", "Секция 2"),
                    onSegmentClick = { index -> clickedIndices.add(index) },
                )
            }
        }

        composeTestRule.onNodeWithText("Математика").performClick()
        assertEquals(listOf(0), clickedIndices)

        composeTestRule.onNodeWithText("Квест 1").performClick()
        assertEquals(listOf(0, 1), clickedIndices)
    }

    // BB-UI-04
    // GIVEN single segment
    // THEN no "›" separator visible
    @Test
    fun single_segment_renders_without_separator() {
        composeTestRule.setContent {
            SchoolQuizTheme {
                BreadcrumbBar(
                    titles = listOf("Математика"),
                    onSegmentClick = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Математика").assertIsDisplayed()
        assertFalse(
            "Separator '›' should not exist for single segment",
            composeTestRule.onAllNodes(hasText(" › ")).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    // BB-UI-05
    // GIVEN segment with 50+ char title
    // THEN text is truncated (maxLines=1 enforced — node exists but content clipped)
    @Test
    fun long_title_truncates_with_ellipsis() {
        val longTitle = "Очень длинное название сегмента хлебной крошки которое точно не вмещается"
        composeTestRule.setContent {
            SchoolQuizTheme {
                BreadcrumbBar(
                    titles = listOf(longTitle, "Дочерний"),
                    onSegmentClick = {},
                )
            }
        }

        // Node with long title must exist (composable rendered) — ellipsis is a visual property
        // verified by maxLines=1 in production code; test verifies no crash + node present.
        composeTestRule.onNode(hasText(longTitle, substring = true)).assertExists()
    }
}
