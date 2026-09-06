package com.tpov.schoolquiz.android.feature.local.settings.presentation.ui

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tpov.schoolquiz.android.core.designsystem.noir.NoirTheme
import com.tpov.schoolquiz.android.feature.local.settings.presentation.R
import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.outbox.OutboxCounts
import com.tpov.schoolquiz.shared.core.sync.SyncFrequency
import com.tpov.schoolquiz.shared.core.sync.SyncStatus
import com.tpov.schoolquiz.shared.core.sync.formatSyncTimestamp
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Locale-independent compose coverage of the sync group: every expected string is resolved from
 * resources through the application context, never written as a literal.
 */
@RunWith(AndroidJUnit4::class)
class NoirSettingsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private var syncFrequencyCalls = 0
    private var profileFrequencyCalls = 0
    private var selectedSyncFrequency: SyncFrequency? = null
    private var selectedProfileFrequency: SyncFrequency? = null
    private var syncNowCalls = 0
    private var forceResyncCalls = 0

    private val statusState = mutableStateOf(SyncStatus())

    private fun setContent(status: SyncStatus = SyncStatus()) {
        statusState.value = status
        syncFrequencyCalls = 0
        profileFrequencyCalls = 0
        selectedSyncFrequency = null
        selectedProfileFrequency = null
        syncNowCalls = 0
        forceResyncCalls = 0
        composeRule.setContent {
            NoirTheme {
                NoirSettingsScreen(
                    profile = UserProfile.offline(),
                    appVersionName = "test",
                    appVersionCode = 1,
                    onSyncNow = { syncNowCalls++ },
                    onSyncFrequencySelected = {
                        syncFrequencyCalls++
                        selectedSyncFrequency = it
                    },
                    onProfileSyncFrequencySelected = {
                        profileFrequencyCalls++
                        selectedProfileFrequency = it
                    },
                    syncStatus = statusState.value,
                    onForceResync = { forceResyncCalls++ },
                )
            }
        }
    }

    private fun scrollTo(text: String) {
        composeRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasText(text))
    }

    /** Всё, что экран сейчас показывает словами, — чтобы утверждать и отсутствие тоже. */
    private fun displayedTexts(): List<String> =
        composeRule
            .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .flatMap { node -> node.config[SemanticsProperties.Text].map { it.text } }

    @Test
    fun `sync group renders both frequency rows with the default daily value`() {
        setContent()

        scrollTo(context.getString(R.string.settings_sync_frequency))

        composeRule
            .onNodeWithText(context.getString(R.string.settings_sync_frequency))
            .assertExists()
        composeRule
            .onNodeWithText(context.getString(R.string.settings_sync_frequency_profile))
            .assertExists()
        composeRule
            .onAllNodesWithText(context.getString(R.string.settings_sync_freq_daily))
            .assertCountEquals(2)
    }

    @Test
    fun `picking an option in the content dialog reports it exactly once`() {
        setContent()

        scrollTo(context.getString(R.string.settings_sync_frequency))
        composeRule
            .onNodeWithText(context.getString(R.string.settings_sync_frequency))
            .performClick()
        composeRule
            .onNodeWithText(context.getString(R.string.settings_sync_freq_weekly))
            .performClick()

        assertEquals(1, syncFrequencyCalls)
        assertEquals(SyncFrequency.WEEKLY, selectedSyncFrequency)
    }

    @Test
    fun `picking an option in the profile dialog reports it exactly once`() {
        setContent()

        scrollTo(context.getString(R.string.settings_sync_frequency_profile))
        composeRule
            .onNodeWithText(context.getString(R.string.settings_sync_frequency_profile))
            .performClick()
        composeRule
            .onNodeWithText(context.getString(R.string.settings_sync_freq_3days))
            .performClick()

        assertEquals(1, profileFrequencyCalls)
        assertEquals(SyncFrequency.EVERY_3_DAYS, selectedProfileFrequency)
    }

    @Test
    fun `sync now row invokes the manual sync callback`() {
        setContent()

        scrollTo(context.getString(R.string.settings_sync_now))
        composeRule
            .onNodeWithText(context.getString(R.string.settings_sync_now))
            .performClick()

        assertEquals(1, syncNowCalls)
    }

    @Test
    fun `a device that never synced says so, rather than looking clean`() {
        // «Всё отправлено» на устройстве, которое ни разу не синхронизировалось, — ложь, и ровно
        // та, из-за которой игрок не пойдёт искать, почему у него пустой каталог.
        setContent()

        scrollTo(context.getString(R.string.settings_sync_state))

        composeRule
            .onNodeWithText(context.getString(R.string.settings_sync_state_never))
            .assertExists()
    }

    @Test
    fun `every outbox state gets its own line with its own number`() {
        // AD-14: четыре состояния — четыре строки и четыре числа. Свести их в одно «ожидает»
        // значит скрыть ровно те два случая, где игроку есть что делать.
        setContent(
            SyncStatus(
                counts =
                    OutboxCounts(
                        waiting = 3,
                        waitingPrecondition = 1,
                        quarantined = 2,
                        conflicted = 1,
                    ),
            ),
        )

        scrollTo(context.getString(R.string.settings_sync_state))

        listOf(
            context.getString(R.string.settings_sync_count_waiting, 3),
            context.getString(R.string.settings_sync_count_waiting_precondition, 1),
            context.getString(R.string.settings_sync_count_quarantined, 2),
            context.getString(R.string.settings_sync_count_conflicted, 1),
        ).forEach { expected ->
            composeRule.onAllNodesWithText(expected).assertCountEquals(1)
        }
    }

    @Test
    fun `no line adds the states up`() {
        // Тест ищет сумму и падает при её появлении: «7 ожидает» одинаково выглядит и когда всё
        // уедет само, и когда шесть записей из семи не уедут никогда.
        setContent(
            SyncStatus(
                counts =
                    OutboxCounts(
                        waiting = 3,
                        waitingPrecondition = 1,
                        quarantined = 2,
                        conflicted = 1,
                    ),
            ),
        )

        scrollTo(context.getString(R.string.settings_sync_state))
        val texts = displayedTexts()

        // 7 — всё вместе, 4 — «ждёт отправки» с предусловием заодно.
        assertTrue(texts.none { it.contains("7") })
        assertTrue(texts.none { it.contains("4") })
        // 3 на экране ровно одна — та, что стоит у `ЖДЁТ`. Сумма «карантин + конфликт» дала бы
        // вторую.
        assertEquals(1, texts.count { it.contains("3") })
    }

    @Test
    fun `re-reading everything asks before it starts`() {
        // Это перекачка всего содержимого; нажать её случайно на мобильном интернете обидно.
        setContent()

        scrollTo(context.getString(R.string.settings_resync))
        composeRule
            .onNodeWithText(context.getString(R.string.settings_resync))
            .performClick()

        assertEquals(0, forceResyncCalls)
        composeRule
            .onNodeWithText(context.getString(R.string.settings_resync_confirm))
            .assertExists()
    }

    @Test
    fun `confirming the dialog starts the re-read exactly once`() {
        setContent()

        scrollTo(context.getString(R.string.settings_resync))
        composeRule.onNodeWithText(context.getString(R.string.settings_resync)).performClick()
        composeRule
            .onNodeWithText(context.getString(R.string.settings_resync_confirm_action))
            .performClick()

        assertEquals(1, forceResyncCalls)
    }

    @Test
    fun `dismissing the dialog starts nothing`() {
        setContent()

        scrollTo(context.getString(R.string.settings_resync))
        composeRule.onNodeWithText(context.getString(R.string.settings_resync)).performClick()
        composeRule
            .onNodeWithText(context.getString(R.string.settings_resync_cancel))
            .performClick()

        assertEquals(0, forceResyncCalls)
    }

    @Test
    fun `changes nobody could read are said out loud, without a red flag`() {
        // Игроку тут делать нечего: ни повтор, ни «Перечитать всё» такую запись не разберут. Но и
        // молчать нельзя — часть содержимого осталась старой и без этой строки выглядит свежей.
        setContent(SyncStatus(lastSuccessAtMs = SUCCEEDED_AT_MS, unreadableChanges = 4))

        scrollTo(context.getString(R.string.settings_sync_state))

        composeRule
            .onNodeWithText(context.getString(R.string.settings_sync_note_unreadable, 4))
            .assertExists()
        composeRule
            .onNodeWithText(formatSyncTimestamp(SUCCEEDED_AT_MS))
            .assertExists()
    }

    @Test
    fun `a stuck action and an unreadable record are both said, neither hides the other`() {
        // Разные вещи: застрявшее действие игрок может разрешить, непонятую запись — нет. Раньше
        // первое вытесняло второе, потому что обе делили одну строку.
        setContent(
            SyncStatus(
                lastSuccessAtMs = SUCCEEDED_AT_MS,
                counts = OutboxCounts(quarantined = 1),
                unreadableChanges = 4,
            ),
        )

        scrollTo(context.getString(R.string.settings_sync_state))

        composeRule
            .onNodeWithText(context.getString(R.string.settings_sync_count_quarantined, 1))
            .assertExists()
        composeRule
            .onNodeWithText(context.getString(R.string.settings_sync_note_unreadable, 4))
            .assertExists()
    }

    @Test
    fun `each of the five sync error branches gets its own string, and none leaks server text`() {
        // AD-15: причина названа ветвью, а не текстом ошибки. Ни `reason`, ни `throwable.message`
        // на экран не попадают — оба здесь заполнены нарочно.
        val branches =
            mapOf(
                SyncError.NoNetwork to R.string.settings_sync_error_no_network,
                SyncError.PreconditionNotMet to R.string.settings_sync_error_precondition,
                SyncError.Refused(reason = "not enough nolics") to R.string.settings_sync_error_refused,
                SyncError.VersionConflict(serverVersion = 7L) to R.string.settings_sync_error_version_conflict,
                SyncError.Unknown(IllegalStateException("boom")) to R.string.settings_sync_error_unknown,
            )
        assertEquals(5, branches.size)
        assertEquals(5, branches.values.toSet().size)
        setContent(SyncStatus(lastSuccessAtMs = SUCCEEDED_AT_MS, lastError = SyncError.NoNetwork))

        val shown = mutableSetOf<String>()
        branches.forEach { (error, expectedRes) ->
            statusState.value = SyncStatus(lastSuccessAtMs = SUCCEEDED_AT_MS, lastError = error)
            composeRule.waitForIdle()
            scrollTo(context.getString(R.string.settings_sync_error))
            val expected = context.getString(expectedRes)
            composeRule.onNodeWithText(expected).assertExists()
            shown += expected
            val texts = displayedTexts()
            assertTrue(texts.none { it.contains("not enough nolics") })
            assertTrue(texts.none { it.contains("boom") })
            assertTrue(texts.none { it.contains("SyncError") })
        }

        assertEquals(5, shown.size)
    }

    @Test
    fun `a guest snapshot says there was no sync yet, and shows no counter and no reason`() {
        // Пустой uid отдаёт нули, а не отсутствие данных, — и экран обязан прочитать это как
        // «ещё не было», а не как «всё отправлено».
        setContent(SyncStatus())

        scrollTo(context.getString(R.string.settings_sync_state))

        composeRule
            .onNodeWithText(context.getString(R.string.settings_sync_state_never))
            .assertExists()
        composeRule
            .onNodeWithText(context.getString(R.string.settings_sync_error))
            .assertDoesNotExist()
        listOf(
            R.string.settings_sync_count_waiting,
            R.string.settings_sync_count_waiting_precondition,
            R.string.settings_sync_count_quarantined,
            R.string.settings_sync_count_conflicted,
        ).forEach { res ->
            composeRule.onNodeWithText(context.getString(res, 0)).assertDoesNotExist()
        }
    }
}

/** Любой момент, который уже был: экран отличает «синхронизировались» от «ещё ни разу». */
private const val SUCCEEDED_AT_MS = 1_756_800_000_000L
