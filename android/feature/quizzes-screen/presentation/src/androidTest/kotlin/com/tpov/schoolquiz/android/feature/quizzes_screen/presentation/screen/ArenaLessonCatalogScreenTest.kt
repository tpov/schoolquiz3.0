package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.screen

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tpov.schoolquiz.android.core.designsystem.SchoolQuizTheme
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.R
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.ArenaCatalogNotice
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.ArenaLessonCatalogUiState
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLessonCatalogCard
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLessonCatalogPlacement
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLessonCatalogRating
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLessonCatalogScore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class ArenaLessonCatalogScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun initialAndRecomposedScreenMakeNoCallsUntilManualLoad() {
        var loads = 0
        var next = 0
        val state = mutableStateOf(ArenaLessonCatalogUiState(sessionAvailable = true))
        compose.setContent {
            SchoolQuizTheme {
                ArenaLessonCatalogScreen(state.value, { loads++ }, { next++ }, {})
            }
        }
        compose.onNodeWithTag("arena_notice").assertIsDisplayed()
        compose.onNodeWithTag("arena_empty").assertDoesNotExist()
        compose.runOnIdle {
            state.value = state.value.copy(notice = ArenaCatalogNotice.CATALOG_CHANGED)
            assertEquals(0, loads)
            assertEquals(0, next)
        }
        compose.onNodeWithTag("arena_refresh").performClick()
        compose.runOnIdle {
            assertEquals(1, loads)
            assertEquals(0, next)
        }
    }

    @Test
    fun loadedLessonIsMetadataWithoutAnyStartOrCardClick() {
        compose.setContent {
            SchoolQuizTheme {
                ArenaLessonCatalogScreen(
                    ArenaLessonCatalogUiState(listOf(unratedLesson()), 1, sessionAvailable = true, notice = null),
                    {},
                    {},
                    {},
                )
            }
        }
        // Check the header while composed; LazyColumn may dispose it after scrolling.
        compose.onAllNodes(hasClickAction()).assertCountEquals(2)
        compose.onNodeWithTag("arena_lesson_lesson-1").performScrollTo()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
        compose.onNodeWithText(string(R.string.arena_catalog_untitled)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.arena_catalog_author_id, "public-author")).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.arena_catalog_unrated)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.arena_catalog_price, 1)).assertIsDisplayed()
    }

    @Test
    fun loadingDisablesBothManualActionsAndNeverCallsThem() {
        var calls = 0
        compose.setContent {
            SchoolQuizTheme {
                ArenaLessonCatalogScreen(
                    ArenaLessonCatalogUiState(
                        isLoading = true,
                        canLoadMore = true,
                        sessionAvailable = true,
                        notice = null,
                    ),
                    { calls++ },
                    { calls++ },
                    {},
                )
            }
        }
        compose.onNodeWithTag("arena_refresh").assertIsNotEnabled()
        compose.onNodeWithTag("arena_more").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0, calls) }
    }

    @Test
    fun failedReadIsNotAnEmptySuccessfulCatalog() {
        compose.setContent {
            SchoolQuizTheme {
                ArenaLessonCatalogScreen(
                    ArenaLessonCatalogUiState(sessionAvailable = true, notice = ArenaCatalogNotice.INVALID_CATALOG),
                    {},
                    {},
                    {},
                )
            }
        }
        compose.onNodeWithText(string(R.string.arena_catalog_invalid)).assertIsDisplayed()
        compose.onNodeWithTag("arena_empty").assertDoesNotExist()
        compose.onNodeWithTag("arena_count").assertDoesNotExist()
    }

    @Test
    fun confirmedEmptySnapshotShowsARealEmptyStateAndManualRefresh() {
        var loads = 0
        compose.setContent {
            SchoolQuizTheme {
                ArenaLessonCatalogScreen(
                    ArenaLessonCatalogUiState(totalLessons = 0, sessionAvailable = true, notice = null),
                    { loads++ },
                    {},
                    {},
                )
            }
        }
        compose.onNodeWithTag("arena_empty").assertIsDisplayed()
        compose.onNodeWithTag("arena_more").assertDoesNotExist()
        compose.onNodeWithTag("arena_refresh").performClick()
        compose.runOnIdle { assertEquals(1, loads) }
    }

    private fun string(
        id: Int,
        vararg args: Any,
    ): String = InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    private fun unratedLesson() =
        ArenaLessonCatalogCard(
            "lesson-1",
            1,
            null,
            "public-author",
            ArenaLessonCatalogRating(0, 0, ArenaLessonCatalogScore(40, 20), 0),
            1,
            ArenaLessonCatalogPlacement(null, null, null),
        )
}
