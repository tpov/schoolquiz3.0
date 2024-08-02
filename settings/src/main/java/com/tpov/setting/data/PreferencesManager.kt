package com.tpov.setting.data

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.tpov.common.data.model.SettingConfigModel

class PreferencesManager(context: Context) {

    private val preferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    fun saveSettings(config: SettingConfigModel) {
        preferences.edit().apply {
            putString(SettingsKeys.LOGIN, config.login)
            putString(SettingsKeys.PASSWORD, config.password)
            putString(SettingsKeys.NAME, config.name)
            putString(SettingsKeys.NICKNAME, config.nickname)
            putString(SettingsKeys.BIRTHDAY, config.birthday)
            putString(SettingsKeys.CITY, config.city)
            putInt(SettingsKeys.LOGO, config.logo)
            putString(SettingsKeys.LANGUAGES, config.languages)
            putString(SettingsKeys.PROFILE_SYNC_FREQUENCY, config.profileSyncFrequency)
            putString(SettingsKeys.QUESTS_SYNC_FREQUENCY, config.questsSyncFrequency)
            putBoolean(SettingsKeys.NOTIFICATIONS, config.notificationsEnabled)
            putString(SettingsKeys.EVENT_NOTIFICATIONS_FREQUENCY, config.eventNotificationsFrequency)
            putString(SettingsKeys.LESSONS_FREQUENCY_TIME, config.lessonsFrequencyTime)
            putStringSet(SettingsKeys.LESSONS_FREQUENCY_DAYS, config.lessonsFrequencyDays)
            apply()
        }
    }

    fun getSettings(): SettingConfigModel {
        return SettingConfigModel(
            preferences.getString(SettingsKeys.LOGIN, "") ?: "",
            preferences.getString(SettingsKeys.PASSWORD, "") ?: "",
            preferences.getString(SettingsKeys.NAME, "") ?: "",
            preferences.getString(SettingsKeys.NICKNAME, "") ?: "",
            preferences.getString(SettingsKeys.BIRTHDAY, "") ?: "",
            preferences.getString(SettingsKeys.CITY, "") ?: "",
            preferences.getInt(SettingsKeys.LOGO, -1),
            preferences.getString(SettingsKeys.LANGUAGES, "") ?: "",
            preferences.getString(SettingsKeys.PROFILE_SYNC_FREQUENCY, "1") ?: "1",
            preferences.getString(SettingsKeys.QUESTS_SYNC_FREQUENCY, "1") ?: "1",
            preferences.getBoolean(SettingsKeys.NOTIFICATIONS, false),
            preferences.getString(SettingsKeys.EVENT_NOTIFICATIONS_FREQUENCY, "1") ?: "1",
            preferences.getString(SettingsKeys.LESSONS_FREQUENCY_TIME, "00:00") ?: "00:00",
            preferences.getStringSet(SettingsKeys.LESSONS_FREQUENCY_DAYS, emptySet()) ?: emptySet()
        )
    }
}