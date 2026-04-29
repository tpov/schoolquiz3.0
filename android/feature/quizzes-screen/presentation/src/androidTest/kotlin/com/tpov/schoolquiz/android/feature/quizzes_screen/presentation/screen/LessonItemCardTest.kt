package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.screen

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.LessonItemUi
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented Compose UI tests for [LessonItemCard].
 *
 * Spec: docs/features/lesson-runner/plan/phase-06/tests.md §Scenario Group C
 * AC: AC-47 (CT-22), AC-50 (CT-23), AC-51 (CT-24)
 *
 * CT-22: StarRating renders with bestStarsRawTenths=15 (smoke — no test tags on stars)
 * CT-23: hardUnlocked=false → no Checkbox
 * CT-24: hardUnlocked=true  → Checkbox visible and unchecked
 */
@RunWith(AndroidJUnit4::class)
class LessonItemCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // CT-22
    // GIVEN LessonItemUi(bestStarsRawTenths=15, hardUnlocked=false)
    // WHEN LessonItemCard rendered
    // THEN lesson title is visible (smoke — StarRating has no test tags)
    @Test
    fun ct22_lessonItemCard_bestStarsRawTenths15_rendersWithoutCrash() {
        val item = LessonItemUi(
            id = "l1",
            title = "Урок 1",
            orderLabel = "1.",
            bestStarsRawTenths = 15,
            hardUnlocked = false,
        )
        composeTestRule.setContent {
            SchoolQuizTheme {
                LessonItemCard(item = item, onClick = {}, onHardCheckChanged = {})
            }
        }
        composeTestRule.onNodeWithText("Урок 1").assertIsDisplayed()
    }

    // CT-23
    // GIVEN LessonItemUi(hardUnlocked=false)
    // WHEN LessonItemCard rendered
    // THEN Checkbox is not present (hardUnlocked=false → Checkbox branch skipped)
    @Test
    fun ct23_lessonItemCard_hardUnlocked_false_noCheckbox() {
        val item = LessonItemUi(
            id = "l2",
            title = "Урок 2",
            hardUnlocked = false,
        )
        composeTestRule.setContent {
            SchoolQuizTheme {
                LessonItemCard(item = item, onClick = {}, onHardCheckChanged = {})
            }
        }
        composeTestRule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)).assertCountEquals(0)
    }

    // CT-24
    // GIVEN LessonItemUi(hardUnlocked=true, isHardChecked=false)
    // WHEN LessonItemCard rendered
    // THEN exactly one Checkbox visible
    @Test
    fun ct24_lessonItemCard_hardUnlocked_true_checkboxVisible() {
        val item = LessonItemUi(
            id = "l3",
            title = "Урок 3",
            hardUnlocked = true,
            isHardChecked = false,
        )
        composeTestRule.setContent {
            SchoolQuizTheme {
                LessonItemCard(item = item, onClick = {}, onHardCheckChanged = {})
            }
        }
        composeTestRule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)).assertCountEquals(1)
    }
}
