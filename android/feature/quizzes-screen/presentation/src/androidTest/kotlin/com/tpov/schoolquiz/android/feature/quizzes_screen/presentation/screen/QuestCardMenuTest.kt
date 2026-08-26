package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.screen

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
import com.tpov.schoolquiz.android.core.designsystem.model.QuestDisplayItem
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.R
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeQuestListComponent
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.QuestListUiState
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue

/**
 * Instrumented Compose UI tests for QuestListScreen long-press share menu.
 *
 * Spec traceability: docs/features/quizzes-screen/plan/phase-06/tests.md §QuestCardMenuTest
 * AC coverage: QC-UI-01 (menu opens), QC-UI-02 (dismiss), QC-UI-03 (order: dismiss then intent),
 *              QC-UI-04 (ActivityNotFoundException silent), QC-UI-05 (haptic feedback)
 *
 * Tests operate at QuestListScreen level (not card level). After Phase-06 migration,
 * long-press is handled by HierarchyItemCard.onLongClick wired in QuestListScreen — all
 * tests remain valid because they go through onNodeWithText("Квест А").performTouchInput { longClick() }.
 *
 * QC-UI-03 uses Espresso Intents (androidx.test.espresso:espresso-intents) to verify
 * Intent.ACTION_CHOOSER dispatched. Requires scaffold:
 *   androidTestImplementation(libs.androidx.test.espresso.intents) in build.gradle.kts.
 *
 * Requires Phase-06 QuestListScreen with DropdownMenu and Modifier.testTag("quest_menu").
 */
@RunWith(AndroidJUnit4::class)
class QuestCardMenuTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun stringRes(res: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(res)

    private fun questItem(id: String = "q-a", title: String = "Квест А") = QuestDisplayItem(
        id = QuestId(id),
        catalogId = CatalogId("cat-1"),
        title = title,
        pictureUrl = null,
        averageRating = null,
    )

    // QC-UI-01
    // GIVEN QuestListScreen with one quest
    // WHEN user long-presses the quest card
    // THEN DropdownMenu tagged "quest_menu" becomes visible with the share item
    @Test
    fun qcUi01_longPress_opens_dropdownMenu() {
        val questA = questItem()
        val fake = FakeQuestListComponent(
            initialState = QuestListUiState.Loaded(listOf(questA)),
        )

        composeTestRule.setContent {
            SchoolQuizTheme {
                QuestListScreen(component = fake, onSegmentClick = {})
            }
        }

        composeTestRule.onNodeWithText("Квест А").performTouchInput { longClick() }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("quest_menu").assertIsDisplayed()
        composeTestRule.onNodeWithText(stringRes(R.string.quizzes_menu_share)).assertIsDisplayed()
    }

    // QC-UI-02
    // GIVEN DropdownMenu is open (QC-UI-01 precondition)
    // WHEN user taps outside the menu
    // THEN the share item node no longer exists in composition
    @Test
    fun qcUi02_tapOutside_closes_menu() {
        val questA = questItem()
        val fake = FakeQuestListComponent(
            initialState = QuestListUiState.Loaded(listOf(questA)),
        )

        composeTestRule.setContent {
            SchoolQuizTheme {
                QuestListScreen(component = fake, onSegmentClick = {})
            }
        }

        composeTestRule.onNodeWithText("Квест А").performTouchInput { longClick() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("quest_menu").assertIsDisplayed()

        composeTestRule.onNodeWithTag("quest_menu_dismiss_layer").performTouchInput { click() }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(stringRes(R.string.quizzes_menu_share)).assertDoesNotExist()
        composeTestRule.onNodeWithTag("quest_menu").assertDoesNotExist()
    }

    // QC-UI-03
    // GIVEN DropdownMenu is open
    // WHEN user taps the share item
    // THEN menu dismisses first (expandedQuestId=null, text gone), then Intent.ACTION_CHOOSER fired
    //
    // Order invariant (ADR-QS-08): expandedQuestId = null BEFORE startActivity.
    // Verified: after performClick() menu is already gone AND Intents.intended() finds ACTION_CHOOSER.
    // Requires scaffold: androidTestImplementation(libs.androidx.test.espresso.intents)
    @Test
    fun qcUi03_menu_closed_before_share_intent_fired() {
        val questA = questItem()
        val fake = FakeQuestListComponent(
            initialState = QuestListUiState.Loaded(listOf(questA)),
        )

        composeTestRule.setContent {
            SchoolQuizTheme {
                QuestListScreen(component = fake, onSegmentClick = {})
            }
        }

        Intents.init()
        try {
            composeTestRule.onNodeWithText("Квест А").performTouchInput { longClick() }
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("quest_menu").assertIsDisplayed()

            composeTestRule.onNodeWithText(stringRes(R.string.quizzes_menu_share)).performClick()
            composeTestRule.waitForIdle()

            // Menu dismissed first (expandedQuestId = null was executed before startActivity)
            composeTestRule.onNodeWithText(stringRes(R.string.quizzes_menu_share)).assertDoesNotExist()
            composeTestRule.onNodeWithTag("quest_menu").assertDoesNotExist()

            // Share intent dispatched: Intent.createChooser wraps ACTION_SEND in ACTION_CHOOSER
            Intents.intended(hasAction(Intent.ACTION_CHOOSER))
        } finally {
            Intents.release()
        }
    }

    // QC-UI-04
    // GIVEN context.startActivity throws ActivityNotFoundException
    // WHEN user taps the share item
    // THEN no crash; menu closes; test completes successfully
    @Test
    fun qcUi04_activityNotFoundException_handled_silently() {
        val questA = questItem()
        val fake = FakeQuestListComponent(
            initialState = QuestListUiState.Loaded(listOf(questA)),
        )
        val throwingContext = object : ContextWrapper(
            ApplicationProvider.getApplicationContext<Context>()
        ) {
            override fun startActivity(intent: Intent) {
                throw ActivityNotFoundException("Test: no activity for ACTION_SEND")
            }
        }

        composeTestRule.setContent {
            CompositionLocalProvider(LocalContext provides throwingContext) {
                SchoolQuizTheme {
                    QuestListScreen(component = fake, onSegmentClick = {})
                }
            }
        }

        composeTestRule.onNodeWithText("Квест А").performTouchInput { longClick() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(stringRes(R.string.quizzes_menu_share)).performClick()
        composeTestRule.waitForIdle()

        // No crash — test reaches this point; menu is dismissed
        composeTestRule.onNodeWithText(stringRes(R.string.quizzes_menu_share)).assertDoesNotExist()
    }

    // QC-UI-05
    // GIVEN mock HapticFeedback provided via CompositionLocalProvider
    // WHEN user long-presses the quest card
    // THEN HapticFeedbackType.LongPress is recorded
    //
    // Note: requires production code to call LocalHapticFeedback.current.performHapticFeedback()
    // in the QuestListScreen onLongClick handler or QuestCard internals.
    @Test
    fun qcUi05_hapticFeedback_fired_on_long_press() {
        val questA = questItem()
        val fake = FakeQuestListComponent(
            initialState = QuestListUiState.Loaded(listOf(questA)),
        )
        val hapticCalls = mutableListOf<HapticFeedbackType>()
        val trackingHapticFeedback = object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                hapticCalls.add(hapticFeedbackType)
            }
        }

        composeTestRule.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides trackingHapticFeedback) {
                SchoolQuizTheme {
                    QuestListScreen(component = fake, onSegmentClick = {})
                }
            }
        }

        composeTestRule.onNodeWithText("Квест А").performTouchInput { longClick() }
        composeTestRule.waitForIdle()

        assertTrue(
            hapticCalls.contains(HapticFeedbackType.LongPress),
            "HapticFeedbackType.LongPress must be triggered via LocalHapticFeedback on long press",
        )
    }

}
