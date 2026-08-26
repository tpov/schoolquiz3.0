package com.tpov.schoolquiz.android.feature.app_shell.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
import com.tpov.schoolquiz.android.feature.app_shell.presentation.R
import com.tpov.schoolquiz.android.feature.app_shell.presentation.ui.drawer.DrawerHeader
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.UserStats
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for DrawerHeader composable.
 * Spec: overview.md tests — drawer_header_renders_nickname, drawer_header_streak_bar_progress.
 *
 * Expected strings resolve through the target context resources, so the assertions hold on any
 * device locale.
 */
@RunWith(AndroidJUnit4::class)
class DrawerHeaderTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun string(res: Int, vararg args: Any): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(res, *args)

    // GIVEN DrawerHeader with nickname "Alice"
    // WHEN composed THEN Text("Alice") visible
    @Test
    fun drawer_header_renders_nickname() {
        composeTestRule.setContent {
            SchoolQuizTheme {
                DrawerHeader(userStats = UserStats.guest().copy(nickname = "Alice"), giftBoxCount = 0)
            }
        }
        composeTestRule.onNodeWithText("Alice").assertIsDisplayed()
    }

    // GIVEN DrawerHeader with hasPremium = true
    // WHEN composed THEN "Premium" badge visible
    @Test
    fun drawer_header_renders_premium_badge() {
        composeTestRule.setContent {
            SchoolQuizTheme {
                DrawerHeader(userStats = UserStats.guest().copy(hasPremium = true), giftBoxCount = 0)
            }
        }
        composeTestRule.onNodeWithText(string(R.string.drawer_premium)).assertIsDisplayed()
    }

    // GIVEN DrawerHeader with hasPremium = false
    // WHEN composed THEN "Premium" badge NOT visible
    @Test
    fun drawer_header_hides_premium_badge_for_non_premium() {
        composeTestRule.setContent {
            SchoolQuizTheme {
                DrawerHeader(userStats = UserStats.guest().copy(hasPremium = false), giftBoxCount = 0)
            }
        }
        composeTestRule.onNodeWithText(string(R.string.drawer_premium), useUnmergedTree = true)
            .assertDoesNotExist()
    }

    // GIVEN DrawerHeader with streakDays = 5
    // WHEN composed THEN streak label visible
    @Test
    fun drawer_header_streak_bar_progress_label() {
        composeTestRule.setContent {
            SchoolQuizTheme {
                DrawerHeader(userStats = UserStats.guest().copy(streakDays = 5), giftBoxCount = 0)
            }
        }
        composeTestRule
            .onNodeWithText(string(R.string.drawer_streak_days, 5, 10))
            .assertIsDisplayed()
    }

    // GIVEN DrawerHeader with blank nickname
    // WHEN composed THEN fallback guest name visible
    @Test
    fun drawer_header_blank_nickname_shows_guest_fallback() {
        composeTestRule.setContent {
            SchoolQuizTheme {
                DrawerHeader(userStats = UserStats.guest().copy(nickname = ""), giftBoxCount = 0)
            }
        }
        composeTestRule.onNodeWithText(string(R.string.drawer_guest)).assertIsDisplayed()
    }

}
