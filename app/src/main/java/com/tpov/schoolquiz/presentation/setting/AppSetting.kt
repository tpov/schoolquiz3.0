package com.tpov.schoolquiz.presentation.setting

import android.content.Context
import android.content.SharedPreferences

class AppSettings(context: Context) {

    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE)

    var background: String
        get() = sharedPreferences.getString(KEY_BACKGROUND, SHARED_PREFS_NAME) ?: SHARED_PREFS_NAME
        set(value) = sharedPreferences.edit().putString(KEY_BACKGROUND, value).apply()

    var music: String
        get() = sharedPreferences.getString(KEY_MUSIC, KEY_MUSIC) ?: KEY_MUSIC
        set(value) = sharedPreferences.edit().putString(KEY_MUSIC, value).apply()

    var nickColor: String
        get() = sharedPreferences.getString(KEY_NICK_COLOR, KEY_NICK_COLOR) ?: KEY_NICK_COLOR
    set(value) = sharedPreferences.edit().putString(KEY_NICK_COLOR, value).apply()

    var languages: String
        get() = sharedPreferences.getString(KEY_LANGUAGES, KEY_LANGUAGES) ?: KEY_LANGUAGES
        set(value) = sharedPreferences.edit().putString(KEY_LANGUAGES, value).apply()

    companion object {
        private const val SHARED_PREFS_NAME = "quiz_settings"
        private const val KEY_BACKGROUND = "background"
        private const val KEY_MUSIC = "music"
        private const val KEY_NICK_COLOR = "nick_color"
        private const val KEY_LANGUAGES = "languages"
    }
}
