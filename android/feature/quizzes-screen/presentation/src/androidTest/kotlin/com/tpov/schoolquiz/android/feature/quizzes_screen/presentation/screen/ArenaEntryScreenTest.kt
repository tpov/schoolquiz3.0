package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.screen

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
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
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.ArenaEntryDialog
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.ArenaEntryEvent
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.ArenaEntryPage
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.ArenaEntryUiState
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.ArenaLessonCatalogUiState
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLessonCatalogCard
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLessonCatalogPlacement
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLessonCatalogRating
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLessonCatalogScore
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaWalletObservation
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionStamp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class ArenaEntryScreenTest {
    @get:Rule val compose = createComposeRule()
    private val stamp = ArenaSessionStamp("owner", 1)

    @Test
    fun unknownWalletAndFailedCatalogStillAllowExplicitTicketLoadWithoutAutomaticCalls() {
        val events = mutableListOf<ArenaEntryEvent>()
        compose.setContent {
            SchoolQuizTheme {
                ArenaLessonCatalogScreen(
                    ArenaLessonCatalogUiState(sessionAvailable = true, notice = ArenaCatalogNotice.UNAVAILABLE),
                    {},
                    {},
                    {},
                    entryState = ArenaEntryUiState(session = stamp),
                    entryCallbacks = ArenaEntryCallbacks(onEvent = { events += it }),
                )
            }
        }
        compose.runOnIdle { assertTrue(events.isEmpty()) }
        compose.onNodeWithTag("arena_wallet_unknown").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("arena_tickets_load").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(listOf<ArenaEntryEvent>(ArenaEntryEvent.Load(stamp, ArenaEntryPage.TICKETS)), events)
        }
    }

    @Test
    fun confirmedZeroBalanceStillAllowsRecoveryAndBusyDisablesActions() {
        val state =
            mutableStateOf(
                ArenaEntryUiState(
                    session = stamp,
                    wallet = ArenaWalletObservation(stamp, 1000, 0, 30, 0, 2, 500),
                ),
            )
        compose.setContent {
            SchoolQuizTheme {
                ArenaLessonCatalogScreen(
                    ArenaLessonCatalogUiState(),
                    {},
                    {},
                    {},
                    entryState = state.value,
                )
            }
        }
        compose.onNodeWithTag("arena_recovery_load").performScrollTo().assertIsEnabled()
        compose.runOnIdle { state.value = state.value.copy(busy = true) }
        compose.onNodeWithTag("arena_recovery_load").assertIsNotEnabled()
    }

    @Test
    fun dialogRetainsFrozenPriceDespiteCatalogRefreshAndConfirmRetainsEpoch() {
        val events = mutableListOf<ArenaEntryEvent>()
        val catalog = mutableStateOf(ArenaLessonCatalogUiState(lessons = listOf(lesson(1)), session = stamp))
        val frozen =
            ArenaEntryUiState(
                session = stamp,
                dialog = ArenaEntryDialog.Prepare("dialog-original", lesson(1), Difficulty.HARD),
            )
        compose.setContent {
            SchoolQuizTheme {
                ArenaLessonCatalogScreen(
                    catalog.value,
                    {},
                    {},
                    {},
                    entryState = frozen,
                    entryCallbacks = ArenaEntryCallbacks(onEvent = { events += it }),
                )
            }
        }
        compose.runOnIdle { catalog.value = catalog.value.copy(lessons = listOf(lesson(100))) }
        val text =
            InstrumentationRegistry.getInstrumentation().targetContext.getString(
                R.string.arena_entry_price_consent,
                1,
            )
        compose.onNodeWithText(text).assertIsDisplayed()
        compose.onNodeWithTag("arena_entry_confirm").performClick()
        compose.runOnIdle {
            assertEquals(listOf<ArenaEntryEvent>(ArenaEntryEvent.Confirm(stamp, "dialog-original")), events)
        }
    }

    private fun lesson(price: Int) =
        ArenaLessonCatalogCard(
            "lesson",
            1,
            "Frozen title",
            "author",
            ArenaLessonCatalogRating(0, 0, ArenaLessonCatalogScore(40, 20), 0),
            price,
            ArenaLessonCatalogPlacement(null, null, null),
        )
}
