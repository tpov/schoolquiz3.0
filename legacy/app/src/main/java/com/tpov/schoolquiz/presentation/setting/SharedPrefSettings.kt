package com.tpov.schoolquiz.presentation.setting

import android.content.Context
import android.content.SharedPreferences
import com.tpov.schoolquiz.presentation.core.Logcat

object SharedPrefSettings {
    private const val PREFS_ICON = "icon_profile"
    private const val PREFS_SETTING = "profile_setting"
    private lateinit var sharedPreferencesIconProfile: SharedPreferences

    var updateProfile = true

    fun initialize(context: Context) {
        sharedPreferencesIconProfile = context.getSharedPreferences(PREFS_SETTING, Context.MODE_PRIVATE)
    }

    fun getProfileIcon(): String {
        if (!::sharedPreferencesIconProfile.isInitialized) {
            throw IllegalStateException("SharedPreferencesManager is not initialized")
        }
        return sharedPreferencesIconProfile.getString(PREFS_ICON, "") ?: ""
    }

    fun setProfileIcon(iconName: String) {
        if (!::sharedPreferencesIconProfile.isInitialized) {
            throw IllegalStateException("SharedPreferencesManager is not initialized")
        }
        val editor = sharedPreferencesIconProfile.edit()
        editor.putString(PREFS_ICON, iconName)
        editor.apply()
    }

    private fun log(msg: String) {
        Logcat.log(msg, "SharedPrefSettings", Logcat.LOG_FRAGMENT)
    }
}