package com.tpov.schoolquiz.presentation.custom

import android.content.Context
import android.content.SharedPreferences

object SharedPreferencesManager {
    private const val PREFS_NAME = "version_quiz"
    private lateinit var sharedPreferences: SharedPreferences

    fun setVersionQuiz(key: String, value: Int) {
        if (!::sharedPreferences.isInitialized) {
            throw IllegalStateException("SharedPreferencesManager is not initialized")
        }
        val editor = sharedPreferences.edit()
        editor.putInt(key, value)
        editor.apply()
    }

    fun getVersionQuiz(key: String): Int {
        if (!::sharedPreferences.isInitialized) {
            throw IllegalStateException("SharedPreferencesManager is not initialized")
        }
        return sharedPreferences.getInt(key, -1)
    }

    fun initialize(context: Context) {
        sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}