package com.tpov.schoolquiz.android.feature.app_shell.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
import com.tpov.schoolquiz.android.feature.app_shell.presentation.R
import com.tpov.schoolquiz.android.feature.app_shell.presentation.ui.drawer.DrawerSectionList
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Destination
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Tab
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.UserStats
import com.tpov.schoolquiz.shared.feature.app_shell.domain.navigation.Navigator
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for [DrawerSectionList] progressive-unlock rendering.
 *
 * Spec traceability (docs/features/app-shell-menu/plan/phase-06/overview.md):
 *  - drawer_section_list_hidden_section_not_rendered
 *  - AC 3: hidden sections NOT rendered (spec FR #20, BR #17)
 */
@RunWith(AndroidJUnit4::class)
class DrawerSectionListTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val noOpNavigator = object : Navigator {
        override fun goTo(destination: Destination) {}
    }

    private fun string(res: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(res)

    // GIVEN tab=INTERNET, guest stats (currentSkill=0, requires 3000 for Arena/Catalog)
    // WHEN DrawerSectionList composed
    // THEN "Арена" and "Каталог" NOT rendered (requiredRoles not met, BR #17)
    @Test
    fun drawer_section_list_hidden_section_not_rendered() {
        composeTestRule.setContent {
            SchoolQuizTheme {
                DrawerSectionList(
                    tab = Tab.INTERNET,
                    userStats = UserStats.guest(),
                    activeSection = null,
                    navigator = noOpNavigator,
                )
            }
        }
        composeTestRule.onNodeWithText(string(R.string.section_arena)).assertDoesNotExist()
        composeTestRule.onNodeWithText(string(R.string.section_catalog)).assertDoesNotExist()
    }

    // GIVEN tab=INTERNET, stats with currentSkill=3000
    // WHEN DrawerSectionList composed
    // THEN "Арена" and "Каталог" ARE visible (threshold exactly met)
    @Test
    fun drawer_section_list_shows_section_at_unlock_boundary() {
        composeTestRule.setContent {
            SchoolQuizTheme {
                DrawerSectionList(
                    tab = Tab.INTERNET,
                    userStats = UserStats.guest().copy(currentSkill = 3_000),
                    activeSection = null,
                    navigator = noOpNavigator,
                )
            }
        }
        composeTestRule.onNodeWithText(string(R.string.section_arena)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.section_catalog)).assertIsDisplayed()
    }
}
