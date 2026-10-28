package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.screen

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirTheme
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.ArenaEntryDialog
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.ArenaEntryEvent
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.ArenaEntryNotice
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.ArenaEntryUiState
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaWalletObservation
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionStamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * NOIR-переодевка входа в арену (эпик 9.3) не смеет тронуть селекторы: риг-джорни 3.x управляются
 * этими testTag. Тест держит их байт-в-байт и проверяет проводку событий.
 */
@RunWith(AndroidJUnit4::class)
class NoirArenaEntryContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val stamp = ArenaSessionStamp("uid-a", 1L)

    private val wallet = ArenaWalletObservation(
        stamp = stamp,
        observedAtMs = 1_770_000_000_000,
        availableNolics = 4,
        reservedNolics = 1,
        storedLifePoints = 100,
        reservedLifePoints = 50,
        lifePointsUpdatedAtMs = 1_770_000_000_000,
    )

    private val events = mutableListOf<ArenaEntryEvent>()

    private val callbacks = ArenaEntryCallbacks({ events.add(it) })

    private fun setContent(state: ArenaEntryUiState) {
        composeRule.setContent {
            NoirTheme {
                LazyColumn(Modifier.fillMaxSize()) {
                    arenaEntryItems(state, callbacks)
                }
                ArenaEntryDialogs(state, callbacks.onEvent)
            }
        }
    }

    @Test
    fun `entry header keeps every rig selector the journeys drive through`() {
        setContent(
            ArenaEntryUiState(
                session = stamp,
                wallet = wallet,
                notice = ArenaEntryNotice.READY,
            ),
        )

        listOf(
            "arena_entry",
            "arena_wallet_nolics",
            "arena_wallet_refresh",
            "arena_random",
            "arena_tickets_load",
            "arena_preparations_load",
            "arena_recovery_load",
            "arena_entry_notice",
        ).forEach { tag ->
            composeRule.onNodeWithTag(tag).assertExists()
        }
    }

    @Test
    fun `a wallet that is not observed says so through its own tag`() {
        setContent(
            ArenaEntryUiState(
                session = stamp,
                wallet = null,
                notice = ArenaEntryNotice.WALLET_UNKNOWN,
            ),
        )

        composeRule.onNodeWithTag("arena_wallet_unknown").assertExists()
    }

    @Test
    fun `a busy screen marks itself with the busy tag`() {
        // Спиннер — бесконечная анимация: часы заморожены, проверяется наличие маркера.
        composeRule.mainClock.autoAdvance = false
        setContent(ArenaEntryUiState(session = stamp, busy = true))

        composeRule.onNodeWithTag("arena_entry_busy").assertExists()
    }

    @Test
    fun `prepare dialog keeps the consent, difficulty and confirm selectors`() {
        setContent(
            ArenaEntryUiState(
                session = stamp,
                dialog = ArenaEntryDialog.Prepare(id = "dlg-1", lesson = null),
            ),
        )

        listOf(
            "arena_entry_dialog",
            "arena_consent_price",
            "arena_difficulty_EASY",
            "arena_difficulty_HARD",
            "arena_entry_confirm",
        ).forEach { tag ->
            composeRule.onNodeWithTag(tag).assertExists()
        }

        composeRule.onNodeWithTag("arena_difficulty_HARD").performClick()
        composeRule.onNodeWithTag("arena_entry_confirm").performClick()

        assertEquals(2, events.size)
        assertTrue(events[0] is ArenaEntryEvent.ChangeDifficulty)
        assertEquals(Difficulty.HARD, (events[0] as ArenaEntryEvent.ChangeDifficulty).difficulty)
        assertTrue(events[1] is ArenaEntryEvent.Confirm)
        assertEquals("dlg-1", (events[1] as ArenaEntryEvent.Confirm).dialogId)
    }
}
