package com.tpov.schoolquiz.android.core.designsystem.components

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
import com.tpov.schoolquiz.android.core.designsystem.model.QuestDisplayItem
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented Compose UI tests for QuestCard — backward-compat onClick + new onLongClick.
 *
 * Spec traceability: docs/features/quizzes-screen/plan/phase-02/tests.md §QuestCardLongClickTest
 * AC coverage: AC#10 partial (menu itself tested in Phase-06)
 * Canonical ref: 04-testing.md §9.3
 */
@RunWith(AndroidJUnit4::class)
class QuestCardLongClickTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testItem = QuestDisplayItem(
        id = QuestId("test-id-1"),
        catalogId = CatalogId("test"),
        title = "Test Quest",
        pictureUrl = null,
        averageRating = null,
    )

    // QC-UI-01 (partial)
    // GIVEN QuestCard with only onClick (onLongClick=null)
    // WHEN performClick
    // THEN onClick fires with item.id
    @Test
    fun existing_onClick_still_works_with_null_onLongClick() {
        var clickedId: QuestId? = null
        composeTestRule.setContent {
            SchoolQuizTheme {
                QuestCard(
                    item = testItem,
                    onClick = { id -> clickedId = id },
                    onLongClick = null,
                )
            }
        }

        composeTestRule.onNodeWithText("Test Quest").performClick()
        assertEquals(QuestId("test-id-1"), clickedId)
    }

    // QC-UI-02 (partial)
    // GIVEN QuestCard with onLongClick lambda
    // WHEN performLongClick
    // THEN lambda fires with item.id
    @Test
    fun onLongClick_fires_on_long_press() {
        var longClickedId: QuestId? = null
        composeTestRule.setContent {
            SchoolQuizTheme {
                QuestCard(
                    item = testItem,
                    onClick = {},
                    onLongClick = { id -> longClickedId = id },
                )
            }
        }

        composeTestRule.onNodeWithText("Test Quest").performTouchInput { longClick() }
        assertEquals(QuestId("test-id-1"), longClickedId)
    }

    // QC-UI-03
    // GIVEN QuestCard with both onClick and onLongClick
    // WHEN click → onClick fires, long-press → onLongClick fires — no interference
    @Test
    fun both_onClick_and_onLongClick_work_independently() {
        var clickCount = 0
        var longClickCount = 0
        composeTestRule.setContent {
            SchoolQuizTheme {
                QuestCard(
                    item = testItem,
                    onClick = { clickCount++ },
                    onLongClick = { longClickCount++ },
                )
            }
        }

        composeTestRule.onNodeWithText("Test Quest").performClick()
        assertEquals(1, clickCount)
        assertEquals(0, longClickCount)

        composeTestRule.onNodeWithText("Test Quest").performTouchInput { longClick() }
        assertEquals(1, clickCount)
        assertEquals(1, longClickCount)
    }

    // QC-UI-04
    // GIVEN QuestCard with onLongClick=null
    // WHEN rendered
    // THEN no long-click accessibility action present (no haptic semantic)
    @Test
    fun null_onLongClick_no_haptic_semantic() {
        composeTestRule.setContent {
            SchoolQuizTheme {
                QuestCard(
                    item = testItem,
                    onClick = {},
                    onLongClick = null,
                )
            }
        }

        composeTestRule.onNodeWithText("Test Quest")
            .assert(SemanticsMatcher("no OnLongClick action") { node ->
                !node.config.contains(SemanticsActions.OnLongClick)
            })
        composeTestRule.onNodeWithText("Test Quest").performTouchInput { longClick() }
    }
}
