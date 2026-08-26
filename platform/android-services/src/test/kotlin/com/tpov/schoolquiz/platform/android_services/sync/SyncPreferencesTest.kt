package com.tpov.schoolquiz.platform.android_services.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.tpov.schoolquiz.shared.core.sync.SyncFrequency
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncPreferencesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `written content frequency reads back`() {
        val preferences = SyncPreferences(context)

        preferences.write(SyncFrequency.EVERY_3_DAYS)

        assertEquals(SyncFrequency.EVERY_3_DAYS, preferences.read())
    }

    @Test
    fun `written profile frequency reads back`() {
        val preferences = SyncPreferences(context)

        preferences.writeProfile(SyncFrequency.WEEKLY)

        assertEquals(SyncFrequency.WEEKLY, preferences.readProfile())
    }

    @Test
    fun `content and profile cadences are stored independently`() {
        val preferences = SyncPreferences(context)

        preferences.write(SyncFrequency.EVERY_3_DAYS)
        preferences.writeProfile(SyncFrequency.MANUAL)

        assertEquals(SyncFrequency.EVERY_3_DAYS, preferences.read())
        assertEquals(SyncFrequency.MANUAL, preferences.readProfile())
    }

    @Test
    fun `fresh preferences default to daily for both cadences`() {
        val preferences = SyncPreferences(context)

        assertEquals(SyncFrequency.DAILY, preferences.read())
        assertEquals(SyncFrequency.DAILY, preferences.readProfile())
    }

    @Test
    fun `corrupted content value falls back to daily`() {
        context
            .getSharedPreferences("schoolquiz_sync", Context.MODE_PRIVATE)
            .edit()
            .putString("sync_frequency", "not-a-frequency")
            .apply()

        assertEquals(SyncFrequency.DAILY, SyncPreferences(context).read())
    }

    @Test
    fun `corrupted profile value falls back to daily`() {
        context
            .getSharedPreferences("schoolquiz_sync", Context.MODE_PRIVATE)
            .edit()
            .putString("profile_sync_frequency", "")
            .apply()

        assertEquals(SyncFrequency.DAILY, SyncPreferences(context).readProfile())
    }
}
