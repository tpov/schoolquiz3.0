package com.tpov.setting.data

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.tpov.common.data.model.SettingConfigModel
import com.tpov.setting.R

class PreferencesManager(var context: Context) {

    private val preferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    fun saveSettings(config: SettingConfigModel) {
        preferences.edit().apply {
            putInt("tpovId", config.tpovId)
            putString(context.getString(R.string.key_login), config.login)
            putString(context.getString(R.string.key_password), config.password)
            putString(context.getString(R.string.key_name), config.name)
            putString(context.getString(R.string.key_nickname), config.nickname)
            putString(context.getString(R.string.key_birthday), config.birthday)
            putString(context.getString(R.string.key_city), config.city)
            putInt(context.getString(R.string.key_logo), config.logo)
            putString(context.getString(R.string.key_languages), config.languages)
            putString(context.getString(R.string.key_profile_sync_frequency), config.profileSyncFrequency)
            putString(context.getString(R.string.key_quests_sync_frequency), config.questsSyncFrequency)
            putBoolean(context.getString(R.string.key_notifications), config.notificationsEnabled)
            putString(context.getString(R.string.key_event_notifications_frequency), config.eventNotificationsFrequency)
            putString(context.getString(R.string.key_lessons_frequency_time), config.lessonsFrequencyTime)
            putStringSet(context.getString(R.string.key_lessons_frequency_days), config.lessonsFrequencyDays)
            apply()
        }
    }

    fun getSettings(): SettingConfigModel {
        val defaultConfig = SettingConfigModel.default()
        return SettingConfigModel(
            preferences.getInt("tpovId", 0),
            preferences.getString(context.getString(R.string.key_login), defaultConfig.login) ?: defaultConfig.login,
            preferences.getString(context.getString(R.string.key_password), defaultConfig.password) ?: defaultConfig.password,
            preferences.getString(context.getString(R.string.key_name), defaultConfig.name) ?: defaultConfig.name,
            preferences.getString(context.getString(R.string.key_nickname), defaultConfig.nickname) ?: defaultConfig.nickname,
            preferences.getString(context.getString(R.string.key_birthday), defaultConfig.birthday) ?: defaultConfig.birthday,
            preferences.getString(context.getString(R.string.key_city), defaultConfig.city) ?: defaultConfig.city,
            preferences.getInt(context.getString(R.string.key_logo), defaultConfig.logo),
            preferences.getString(context.getString(R.string.key_languages), defaultConfig.languages) ?: defaultConfig.languages,
            preferences.getString(context.getString(R.string.key_profile_sync_frequency), defaultConfig.profileSyncFrequency) ?: defaultConfig.profileSyncFrequency,
            preferences.getString(context.getString(R.string.key_quests_sync_frequency), defaultConfig.questsSyncFrequency) ?: defaultConfig.questsSyncFrequency,
            preferences.getBoolean(context.getString(R.string.key_notifications), defaultConfig.notificationsEnabled),
            preferences.getString(context.getString(R.string.key_event_notifications_frequency), defaultConfig.eventNotificationsFrequency) ?: defaultConfig.eventNotificationsFrequency,
            preferences.getString(context.getString(R.string.key_lessons_frequency_time), defaultConfig.lessonsFrequencyTime) ?: defaultConfig.lessonsFrequencyTime,
            preferences.getStringSet(context.getString(R.string.key_lessons_frequency_days), defaultConfig.lessonsFrequencyDays) ?: defaultConfig.lessonsFrequencyDays
        )
    }
}