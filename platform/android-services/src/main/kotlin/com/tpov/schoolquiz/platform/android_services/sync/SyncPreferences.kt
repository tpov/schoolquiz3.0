package com.tpov.schoolquiz.platform.android_services.sync

import android.content.Context
import com.tpov.schoolquiz.shared.core.sync.SyncFrequency

/**
 * The player's sync cadences (content and profile), kept in plain shared preferences next to the
 * design-style choice.
 *
 * Stored by name rather than ordinal so reordering the enum cannot silently reinterpret what an
 * existing install picked.
 */
class SyncPreferences(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun read(): SyncFrequency = read(KEY_FREQUENCY)

    fun readProfile(): SyncFrequency = read(KEY_PROFILE_FREQUENCY)

    fun write(frequency: SyncFrequency) {
        prefs.edit().putString(KEY_FREQUENCY, frequency.name).apply()
    }

    fun writeProfile(frequency: SyncFrequency) {
        prefs.edit().putString(KEY_PROFILE_FREQUENCY, frequency.name).apply()
    }

    private fun read(key: String): SyncFrequency =
        prefs.getString(key, null)
            ?.let { stored -> SyncFrequency.entries.firstOrNull { it.name == stored } }
            ?: SyncFrequency.DAILY

    private companion object {
        const val PREFS_NAME = "schoolquiz_sync"
        const val KEY_FREQUENCY = "sync_frequency"
        const val KEY_PROFILE_FREQUENCY = "profile_sync_frequency"
    }
}
