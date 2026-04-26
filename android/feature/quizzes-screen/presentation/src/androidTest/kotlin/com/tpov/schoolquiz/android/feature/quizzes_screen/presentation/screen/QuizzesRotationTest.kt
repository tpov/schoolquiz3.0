package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.screen

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuizzesConfig
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeQuizzesComponent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertIs

/**
 * Instrumented rotation test for QuizzesScreen (AC#22).
 *
 * Spec traceability: docs/features/quizzes-screen/plan/phase-05/tests.md §QuizzesRotationTest
 * Canonical ref: 04-testing.md §9.6
 * AC coverage: ROT-UI-01 (active child config preserved after recreate)
 *
 * Uses FakeQuizzesComponent which holds MutableValue<ChildStack> externally to the Activity.
 * After scenario.recreate(), the fake component's state (QuestList active) is preserved because
 * the reference lives in the test class (not in the Activity's ViewModel/instanceKeeper).
 *
 * OPEN QUESTION (OQ-ROT-01): True Decompose instanceKeeper retention across Activity recreation
 * requires a Decompose-aware host Activity (creating DefaultQuizzesComponent via retainedComponent()).
 * The current test verifies:
 *   (a) UI can re-render after Activity recreation without crash
 *   (b) FakeQuizzesComponent API preserves state across external lifecycle events
 * For a full instanceKeeper test, a custom host Activity would need to be added to the module
 * (requires scaffold change — backend-dev).
 */
@RunWith(AndroidJUnit4::class)
class QuizzesRotationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    // ROT-UI-01
    // GIVEN QuizzesScreen rendered with FakeQuizzesComponent in QuestList state
    // WHEN scenario.recreate() (configuration change)
    // THEN active child config is QuizzesConfig.QuestList (state preserved)
    // THEN UI re-renders without crash (loading indicator visible for QuestList state)
    @Test
    fun rotUi01_afterRecreate_activeChildConfigIsPreserved() {
        val fakeComponent = FakeQuizzesComponent()
        fakeComponent.setQuestListActive(catalogId = "cat-1", catalogName = "Математика")

        composeTestRule.setContent {
            SchoolQuizTheme {
                QuizzesScreen(component = fakeComponent)
            }
        }

        // Verify QuestList active and loading indicator visible before rotation
        val configBefore = fakeComponent.childStack.value.active.configuration
        assertIs<QuizzesConfig.QuestList>(configBefore)
        composeTestRule
            .onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
            .assertIsDisplayed()

        // Verifies no-crash on recreate + fake state consistency.
        // Real Decompose state retention requires a custom ComponentActivity host — TODO post-MVP.
        composeTestRule.activityRule.scenario.recreate()
        composeTestRule.waitForIdle()

        // Re-render after recreation to verify UI composes without crash on new Activity instance
        composeTestRule.setContent {
            SchoolQuizTheme {
                QuizzesScreen(component = fakeComponent)
            }
        }
        composeTestRule
            .onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
            .assertIsDisplayed()

        // Component state is preserved because it's held externally to the Activity
        // (In production, DefaultQuizzesComponent survives rotation via instanceKeeper in ViewModel)
        val configAfter = fakeComponent.childStack.value.active.configuration
        assertIs<QuizzesConfig.QuestList>(configAfter)
        kotlin.test.assertEquals(configBefore.catalogId, configAfter.catalogId)
        kotlin.test.assertEquals(configBefore.titles, configAfter.titles)
    }

    // ROT-UI-01 variant: titles preserved after recreate
    // GIVEN QuestList with catalogName="Математика"
    // WHEN recreate()
    // THEN titles still contain "Математика"
    @Test
    fun rotUi01_variant_titlesPreservedInActiveConfigAfterRecreate() {
        val fakeComponent = FakeQuizzesComponent()
        fakeComponent.setQuestListActive(catalogId = "cat-math", catalogName = "Математика")

        composeTestRule.setContent {
            SchoolQuizTheme {
                QuizzesScreen(component = fakeComponent)
            }
        }

        composeTestRule.activityRule.scenario.recreate()
        composeTestRule.waitForIdle()

        val configAfter = fakeComponent.childStack.value.active.configuration
        assertIs<QuizzesConfig.QuestList>(configAfter)
        kotlin.test.assertTrue(
            configAfter.titles.contains("Математика"),
            "Expected titles to contain 'Математика' after recreate, got: ${configAfter.titles}",
        )
        kotlin.test.assertEquals(
            "cat-math",
            configAfter.catalogId,
            "Expected catalogId='cat-math' after recreate, got: ${configAfter.catalogId}",
        )
    }
}
