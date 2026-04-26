package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.screen

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
import com.tpov.schoolquiz.android.core.designsystem.model.QuestDisplayItem
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake.FakeQuestListComponent
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.QuestListUiState
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Instrumented Compose UI tests for QuestListScreen.
 *
 * Spec traceability: docs/features/quizzes-screen/plan/phase-05/tests.md
 * AC coverage: QLS-UI-01 (loading), QLS-UI-02 (loaded), QLS-UI-03 (empty),
 *              QLS-UI-04 (quest click), QLS-UI-05 (breadcrumb)
 *
 * Uses FakeQuestListComponent (MutableValue-backed) — no real DefaultQuestListComponent needed.
 * Open Question: androidTestImplementation(compose.ui.test.junit4) must be added to
 * android/feature/quizzes-screen/presentation/build.gradle.kts (scaffold change — backend-dev).
 */
@RunWith(AndroidJUnit4::class)
class QuestListScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // QLS-UI-01
    // GIVEN fake component with state=Loading
    // WHEN screen rendered
    // THEN CircularProgressIndicator (indeterminate progress bar) is visible
    @Test
    fun qlsUi01_loadingStateShowsProgressIndicator() {
        val fake = FakeQuestListComponent(initialState = QuestListUiState.Loading)

        composeTestRule.setContent {
            SchoolQuizTheme {
                QuestListScreen(component = fake, onSegmentClick = {})
            }
        }

        composeTestRule
            .onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
            .assertIsDisplayed()
    }

    // QLS-UI-02
    // GIVEN fake component with state=Loaded(quest1, quest2)
    // WHEN screen rendered
    // THEN both quest titles visible (rendered via HierarchyItemCard after Phase-06 migration)
    @Test
    fun qlsUi02_loadedStateShowsAllQuestTitles() {
        val quest1 = questDisplayItem("q-1", "Алгебра")
        val quest2 = questDisplayItem("q-2", "Геометрия")
        val fake = FakeQuestListComponent(
            initialState = QuestListUiState.Loaded(listOf(quest1, quest2)),
        )

        composeTestRule.setContent {
            SchoolQuizTheme {
                QuestListScreen(component = fake, onSegmentClick = {})
            }
        }

        composeTestRule.onNodeWithText("Алгебра").assertIsDisplayed()
        composeTestRule.onNodeWithText("Геометрия").assertIsDisplayed()
    }

    // QLS-UI-03
    // GIVEN fake component with state=Empty
    // WHEN screen rendered
    // THEN "Нет квестов" placeholder visible; no LazyColumn crash with 0 items
    @Test
    fun qlsUi03_emptyStateShowsPlaceholderText() {
        val fake = FakeQuestListComponent(initialState = QuestListUiState.Empty)

        composeTestRule.setContent {
            SchoolQuizTheme {
                QuestListScreen(component = fake, onSegmentClick = {})
            }
        }

        composeTestRule.onNodeWithText("Нет квестов").assertIsDisplayed()
    }

    // QLS-UI-04
    // GIVEN fake component with state=Loaded(questA)
    // WHEN user taps HierarchyItemCard with title "Quest A"
    // THEN onQuestClickCalled equals questA
    @Test
    fun qlsUi04_tapOnQuestCardFiresOnQuestClickWithCorrectItem() {
        val questA = questDisplayItem("q-a", "Quest A")
        val fake = FakeQuestListComponent(
            initialState = QuestListUiState.Loaded(listOf(questA)),
        )

        composeTestRule.setContent {
            SchoolQuizTheme {
                QuestListScreen(component = fake, onSegmentClick = {})
            }
        }

        composeTestRule.onNodeWithText("Quest A").performClick()

        assertNotNull(fake.onQuestClickCalled, "onQuestClick must be called after tap")
        assertEquals(questA, fake.onQuestClickCalled)
    }

    // QLS-UI-05
    // GIVEN fake component with titles=["Math"]
    // WHEN screen rendered
    // THEN "Math" is visible in BreadcrumbBar area
    @Test
    fun qlsUi05_breadcrumbRendersComponentTitles() {
        val fake = FakeQuestListComponent(
            initialState = QuestListUiState.Loading,
            titles = listOf("Math"),
        )

        composeTestRule.setContent {
            SchoolQuizTheme {
                QuestListScreen(component = fake, onSegmentClick = {})
            }
        }

        composeTestRule.onNodeWithText("Math").assertIsDisplayed()
    }

    // QLS-UI-06
    // GIVEN fake component with state=Loaded(quest with averageRating=4.5f)
    // WHEN screen rendered
    // THEN "4.5" is visible (HierarchyItemCard renders rating param)
    @Test
    fun qlsUi06_ratingDisplayedWhenNonNull() {
        val ratedQuest = QuestDisplayItem(
            id = QuestId("q-rated"),
            catalogId = CatalogId("cat-1"),
            title = "Rated Quest",
            pictureUrl = null,
            averageRating = 4.5f,
            averageRatingCount = 42,
        )
        val fake = FakeQuestListComponent(
            initialState = QuestListUiState.Loaded(listOf(ratedQuest)),
        )

        composeTestRule.setContent {
            SchoolQuizTheme {
                QuestListScreen(component = fake, onSegmentClick = {})
            }
        }

        // HierarchyItemCard renders separate Text nodes; "4.5" visible via merged semantics
        composeTestRule.onNodeWithText("4.5").assertIsDisplayed()
    }

    // Edge case: Loaded with single item — only that item visible
    @Test
    fun loadedStateWith1QuestShowsExactlyThatQuest() {
        val singleQuest = questDisplayItem("q-1", "Единственный квест")
        val fake = FakeQuestListComponent(
            initialState = QuestListUiState.Loaded(listOf(singleQuest)),
        )

        composeTestRule.setContent {
            SchoolQuizTheme {
                QuestListScreen(component = fake, onSegmentClick = {})
            }
        }

        composeTestRule.onNodeWithText("Единственный квест").assertIsDisplayed()
    }

    // helpers

    private fun questDisplayItem(id: String, title: String) = QuestDisplayItem(
        id = QuestId(id),
        catalogId = CatalogId("cat-1"),
        title = title,
        pictureUrl = null,
        averageRating = null,
    )
}
