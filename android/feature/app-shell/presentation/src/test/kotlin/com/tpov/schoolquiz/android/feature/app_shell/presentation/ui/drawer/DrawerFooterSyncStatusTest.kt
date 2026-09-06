package com.tpov.schoolquiz.android.feature.app_shell.presentation.ui.drawer

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirTheme
import com.tpov.schoolquiz.android.feature.app_shell.presentation.R
import com.tpov.schoolquiz.shared.core.outbox.OutboxCounts
import com.tpov.schoolquiz.shared.core.sync.SyncStatus
import com.tpov.schoolquiz.shared.core.sync.formatSyncTimestamp
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Destination
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.UserStats
import com.tpov.schoolquiz.shared.feature.app_shell.domain.navigation.Navigator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Шторка про синхронизацию: строка последнего успеха под ручным действием и ничего сверх неё.
 *
 * До этой истории шторка не говорила о синхронизации вообще: нажал «синхронизировать» — и не
 * узнал ни что получилось, ни когда получалось в прошлый раз.
 */
@RunWith(AndroidJUnit4::class)
class DrawerFooterSyncStatusTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val statusState = mutableStateOf(SyncStatus())

    private val noOpNavigator =
        object : Navigator {
            override fun goTo(destination: Destination) = Unit
        }

    private fun setContent(
        status: SyncStatus,
        isDebugBuild: Boolean = true,
    ) {
        statusState.value = status
        composeRule.setContent {
            NoirTheme {
                DrawerFooter(
                    navigator = noOpNavigator,
                    isDebugBuild = isDebugBuild,
                    versionName = "test",
                    userStats = UserStats.guest(),
                    onVersionTap = {},
                    onSyncNow = {},
                    syncStatus = statusState.value,
                )
            }
        }
    }

    /** Всё, что шторка сейчас показывает словами, — чтобы утверждать и отсутствие тоже. */
    private fun displayedTexts(): List<String> =
        composeRule
            .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .flatMap { node -> node.config[SemanticsProperties.Text].map { it.text } }

    @Test
    fun `an empty queue shows the time of the last success and not a single counter`() {
        val succeededAtMs = 1_756_800_000_000L

        setContent(SyncStatus(lastSuccessAtMs = succeededAtMs))

        composeRule
            .onNodeWithText(
                context.getString(R.string.drawer_sync_last_success, formatSyncTimestamp(succeededAtMs)),
            )
            .assertExists()
        composeRule
            .onNodeWithText(context.getString(R.string.drawer_sync_never))
            .assertDoesNotExist()
    }

    @Test
    fun `a queue that is not empty still shows no counters in the drawer`() {
        // Числа очереди разбираются в настройках; шторка — место, откуда синхронизацию запускают.
        val succeededAtMs = 1_756_800_000_000L
        setContent(
            SyncStatus(
                lastSuccessAtMs = succeededAtMs,
                counts = OutboxCounts(waiting = 3, waitingPrecondition = 1, quarantined = 2, conflicted = 1),
            ),
        )

        val expectedTime = context.getString(R.string.drawer_sync_last_success, formatSyncTimestamp(succeededAtMs))
        val texts = displayedTexts()

        assertTrue(texts.contains(expectedTime))
        // Ни одно из чисел снимка на экран не попало: 3, 2 и суммы 4 и 7 искать нечего.
        val others = texts.filter { it != expectedTime && it != "vtest" }
        assertTrue(others.none { it.any(Char::isDigit) })
    }

    @Test
    fun `a guest snapshot says there was no sync yet, in its own words`() {
        setContent(SyncStatus())

        composeRule
            .onNodeWithText(context.getString(R.string.drawer_sync_never))
            .assertExists()
    }
    @Test
    fun `обычный игрок без инструментов разработчика видит строку последнего успеха`() {
        // Ручной запуск синхронизации — инструмент разработчика, и в релизной сборке его в
        // шторке нет. Строка последнего успеха обещана игроку и от этого действия не зависит.
        val succeededAtMs = 1_756_800_000_000L

        setContent(SyncStatus(lastSuccessAtMs = succeededAtMs), isDebugBuild = false)

        composeRule
            .onNodeWithText(context.getString(R.string.footer_sync_now).uppercase())
            .assertDoesNotExist()
        composeRule
            .onNodeWithText(
                context.getString(R.string.drawer_sync_last_success, formatSyncTimestamp(succeededAtMs)),
            )
            .assertExists()
    }

    @Test
    fun `обычному игроку без единого прогона сказано, что синхронизации ещё не было`() {
        setContent(SyncStatus(), isDebugBuild = false)

        composeRule
            .onNodeWithText(context.getString(R.string.footer_sync_now).uppercase())
            .assertDoesNotExist()
        composeRule
            .onNodeWithText(context.getString(R.string.drawer_sync_never))
            .assertExists()
    }

    @Test
    fun `строка последнего успеха на экране одна, а не по одной на каждое действие`() {
        val succeededAtMs = 1_756_800_000_000L

        setContent(SyncStatus(lastSuccessAtMs = succeededAtMs))

        val expected = context.getString(R.string.drawer_sync_last_success, formatSyncTimestamp(succeededAtMs))
        assertEquals(1, displayedTexts().count { it == expected })
    }
}
