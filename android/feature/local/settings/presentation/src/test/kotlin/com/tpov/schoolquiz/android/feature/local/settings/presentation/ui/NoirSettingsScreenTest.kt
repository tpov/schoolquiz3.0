package com.tpov.schoolquiz.android.feature.local.settings.presentation.ui

import android.content.Context
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
import com.tpov.schoolquiz.shared.core.outbox.OutboxCounts
import com.tpov.schoolquiz.shared.core.sync.SyncFrequency
import com.tpov.schoolquiz.shared.core.sync.SyncStatus
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.UserProfile
import org.junit.Assert.assertEquals
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

    private fun setContent(status: SyncStatus = SyncStatus()) {
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
                    syncStatus = status,
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
    fun `actions waiting to be sent are counted on screen`() {
        setContent(SyncStatus(lastSuccessAtMs = 1L, counts = OutboxCounts(waiting = 3)))

        scrollTo(context.getString(R.string.settings_sync_state))

        composeRule
            .onNodeWithText(context.getString(R.string.settings_sync_state_pending, 3))
            .assertExists()
    }

    @Test
    fun `an action that will never be sent outranks the ones that still might`() {
        // Застрявшее важнее ждущего: с первым игроку надо что-то сделать, второе уедет само.
        setContent(
            SyncStatus(lastSuccessAtMs = 1L, counts = OutboxCounts(waiting = 3, quarantined = 2)),
        )

        scrollTo(context.getString(R.string.settings_sync_state))

        composeRule
            .onNodeWithText(context.getString(R.string.settings_sync_state_stuck, 2))
            .assertExists()
        composeRule
            .onNodeWithText(context.getString(R.string.settings_sync_note_quarantine, 2))
            .assertExists()
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
        setContent(SyncStatus(lastSuccessAtMs = 1L, unreadableChanges = 4))

        scrollTo(context.getString(R.string.settings_sync_state))

        composeRule
            .onNodeWithText(context.getString(R.string.settings_sync_note_unreadable, 4))
            .assertExists()
        composeRule
            .onNodeWithText(context.getString(R.string.settings_sync_state_clean))
            .assertExists()
    }

    @Test
    fun `a stuck action still outranks an unreadable record`() {
        // Порядок не случаен: застрявшее действие игрок может разрешить, непонятую запись — нет.
        setContent(
            SyncStatus(
                lastSuccessAtMs = 1L,
                counts = OutboxCounts(quarantined = 1),
                unreadableChanges = 4,
            ),
        )

        scrollTo(context.getString(R.string.settings_sync_state))

        composeRule
            .onNodeWithText(context.getString(R.string.settings_sync_note_quarantine, 1))
            .assertExists()
    }
}
