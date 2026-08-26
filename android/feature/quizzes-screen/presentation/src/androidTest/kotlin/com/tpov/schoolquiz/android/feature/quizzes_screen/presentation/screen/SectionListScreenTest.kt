package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.screen

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.R
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeSectionListComponent
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyItemUi
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyLevel
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.HierarchyListUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Instrumented Compose UI tests for SectionListScreen.
 *
 * Spec traceability: docs/features/quizzes-screen/plan/phase-05/tests.md
 * AC coverage: SLS-UI-01 (loading), SLS-UI-02 (loaded), SLS-UI-03 (empty), SLS-UI-04 (click)
 *
 * Uses FakeSectionListComponent (MutableValue-backed) — no real DefaultSectionListComponent needed.
 */
@RunWith(AndroidJUnit4::class)
class SectionListScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun stringRes(res: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(res)

    // SLS-UI-01
    // GIVEN fake component with state=Loading
    // WHEN screen rendered
    // THEN CircularProgressIndicator (indeterminate) is visible
    @Test
    fun slsUi01_loadingStateShowsProgressIndicator() {
        val fake = FakeSectionListComponent(initialState = HierarchyListUiState.Loading)

        composeTestRule.setContent {
            SchoolQuizTheme {
                SectionListScreen(component = fake, onSegmentClick = {})
            }
        }

        composeTestRule
            .onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
            .assertIsDisplayed()
    }

    // SLS-UI-02
    // GIVEN fake component with state=Loaded(sectionItem)
    // WHEN screen rendered
    // THEN sectionItem.title is visible
    @Test
    fun slsUi02_loadedStateShowsSectionItems() {
        val sectionItem = HierarchyItemUi(id = "s-1", title = "Раздел Алгебры")
        val fake = FakeSectionListComponent(
            initialState = HierarchyListUiState.Loaded(listOf(sectionItem)),
        )

        composeTestRule.setContent {
            SchoolQuizTheme {
                SectionListScreen(component = fake, onSegmentClick = {})
            }
        }

        composeTestRule.onNodeWithText("Раздел Алгебры").assertIsDisplayed()
    }

    // SLS-UI-03
    // GIVEN fake component with state=Empty(HierarchyLevel.SECTIONS)
    // WHEN screen rendered
    // THEN localized empty-sections label visible
    @Test
    fun slsUi03_emptyStateShowsLevelLabel() {
        val fake = FakeSectionListComponent(
            initialState = HierarchyListUiState.Empty(HierarchyLevel.SECTIONS),
        )

        composeTestRule.setContent {
            SchoolQuizTheme {
                SectionListScreen(component = fake, onSegmentClick = {})
            }
        }

        composeTestRule.onNodeWithText(stringRes(R.string.quizzes_empty_sections)).assertIsDisplayed()
    }

    // SLS-UI-04
    // GIVEN fake component with state=Loaded(HierarchyItemUi(id="s-1", title="Section A"))
    // WHEN user taps "Section A"
    // THEN onSectionClickCalled.id == "s-1"
    @Test
    fun slsUi04_tapSectionItemFiresOnSectionClickWithCorrectItem() {
        val sectionA = HierarchyItemUi(id = "s-1", title = "Section A")
        val fake = FakeSectionListComponent(
            initialState = HierarchyListUiState.Loaded(listOf(sectionA)),
        )

        composeTestRule.setContent {
            SchoolQuizTheme {
                SectionListScreen(component = fake, onSegmentClick = {})
            }
        }

        composeTestRule.onNodeWithText("Section A").performClick()

        assertNotNull(fake.onSectionClickCalled, "onSectionClick must be called after tap")
        assertEquals("s-1", fake.onSectionClickCalled?.id)
    }

    // Edge case: BreadcrumbBar with multiple titles renders all
    @Test
    fun breadcrumbWithMultipleTitlesRendersAllSegments() {
        val fake = FakeSectionListComponent(
            initialState = HierarchyListUiState.Loading,
            titles = listOf("Математика", "Квест 1"),
        )

        composeTestRule.setContent {
            SchoolQuizTheme {
                SectionListScreen(component = fake, onSegmentClick = {})
            }
        }

        composeTestRule.onNodeWithText("Математика").assertIsDisplayed()
        composeTestRule.onNodeWithText("Квест 1").assertIsDisplayed()
    }
}
