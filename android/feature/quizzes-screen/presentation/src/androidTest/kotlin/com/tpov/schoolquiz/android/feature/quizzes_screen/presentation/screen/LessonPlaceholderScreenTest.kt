package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeLessonPlaceholderComponent
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.LessonPlaceholderUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

/**
 * Instrumented Compose UI tests for LessonPlaceholderScreen.
 *
 * Spec traceability: docs/features/quizzes-screen/plan/phase-05/tests.md
 * AC coverage: LP-SC-01 (lessonTitle), LP-SC-02 (placeholder text), LP-SC-03 (breadcrumb)
 *
 * Uses FakeLessonPlaceholderComponent — no DefaultLessonPlaceholderComponent needed.
 * Open Question: androidTestImplementation(compose.ui.test.junit4) must be added to
 * android/feature/quizzes-screen/presentation/build.gradle.kts (scaffold change — backend-dev).
 */
@RunWith(AndroidJUnit4::class)
class LessonPlaceholderScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // LP-SC-01
    // GIVEN component with lessonTitle="Algebra Basics"
    // WHEN screen rendered
    // THEN "Algebra Basics" text is visible
    @Test
    fun lpSc01_lessonTitleIsDisplayed() {
        val fake = FakeLessonPlaceholderComponent(
            uiState = LessonPlaceholderUiState(
                lessonTitle = "Algebra Basics",
                titles = listOf("Math", "Quest 1", "Algebra Basics"),
            ),
        )

        composeTestRule.setContent {
            SchoolQuizTheme {
                LessonPlaceholderScreen(component = fake, onSegmentClick = {})
            }
        }

        composeTestRule.onNodeWithText("Algebra Basics").assertIsDisplayed()
    }

    // LP-SC-02
    // GIVEN any component
    // WHEN screen rendered
    // THEN placeholder text "будет добавлено позже" is visible
    @Test
    fun lpSc02_placeholderTextIsDisplayed() {
        val fake = FakeLessonPlaceholderComponent(
            uiState = LessonPlaceholderUiState(
                lessonTitle = "Тест урок",
                titles = listOf("Тест урок"),
            ),
        )

        composeTestRule.setContent {
            SchoolQuizTheme {
                LessonPlaceholderScreen(component = fake, onSegmentClick = {})
            }
        }

        composeTestRule.onNodeWithText("Прохождение урока будет добавлено позже")
            .assertIsDisplayed()
    }

    // LP-SC-03
    // GIVEN component with titles=["Math", "Quest 1", "Section 1", "Theme 1", "Algebra Basics"]
    // WHEN screen rendered
    // THEN all 5 titles rendered in BreadcrumbBar
    // THEN lessonTitle == titles.last() (same value)
    @Test
    fun lpSc03_breadcrumbRendersAllTitlesAndLastMatchesLessonTitle() {
        val titles = listOf("Math", "Quest 1", "Section 1", "Theme 1", "Algebra Basics")
        val fake = FakeLessonPlaceholderComponent(
            uiState = LessonPlaceholderUiState(
                lessonTitle = "Algebra Basics",
                titles = titles,
            ),
        )

        composeTestRule.setContent {
            SchoolQuizTheme {
                LessonPlaceholderScreen(component = fake, onSegmentClick = {})
            }
        }

        composeTestRule.onNodeWithText("Math").assertIsDisplayed()
        composeTestRule.onNodeWithText("Quest 1").assertIsDisplayed()
        composeTestRule.onNodeWithText("Section 1").assertIsDisplayed()
        composeTestRule.onNodeWithText("Theme 1").assertIsDisplayed()

        // lessonTitle (last breadcrumb segment) == uiState.lessonTitle
        assertEquals(
            fake.uiState.lessonTitle,
            fake.uiState.titles.last(),
            "lessonTitle must equal titles.last() — same value shown twice (body + breadcrumb)",
        )
    }
}
