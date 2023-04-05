package com.tpov.schoolquiz.presentation.custom

import android.content.Context
import android.content.SharedPreferences

object SharedPreferencesManager {
    private const val PREFS_NAME_QUIZ = "version_quiz"
    private const val PREFS_NAME_QUESTION = "version_question"
    private lateinit var sharedPreferencesQuiz: SharedPreferences
    private lateinit var sharedPreferencesQuestion: SharedPreferences

    fun setVersionQuiz(key: String, value: Int) {
        log("fun setVersionQuiz key: $key, value: $value")
        if (!::sharedPreferencesQuiz.isInitialized) {
            throw IllegalStateException("SharedPreferencesManager is not initialized")
        }
        val editor = sharedPreferencesQuiz.edit()
        editor.putInt(key, value)
        editor.apply()
    }

    fun getVersionQuiz(key: String): Int {
        log("fun getVersionQuiz key: $key, value: ${sharedPreferencesQuiz.getInt(key, -1)}")
        if (!::sharedPreferencesQuiz.isInitialized) {
            throw IllegalStateException("SharedPreferencesManager is not initialized")
        }
        return sharedPreferencesQuiz.getInt(key, -1)
    }

    fun setVersionQuestion(key: String, value: Int) {
        log("fun setVersionQuiz key: $key, value: $value")
        if (!::sharedPreferencesQuestion.isInitialized) {
            throw IllegalStateException("SharedPreferencesManager is not initialized")
        }
        val editor = sharedPreferencesQuestion.edit()
        editor.putInt(key, value)
        editor.apply()
    }

    fun getVersionQuestion(key: String): Int {
        log("fun getVersionQuiz key: $key, value: ${sharedPreferencesQuestion.getInt(key, -1)}")
        if (!::sharedPreferencesQuestion.isInitialized) {
            throw IllegalStateException("SharedPreferencesManager is not initialized")
        }
        return sharedPreferencesQuestion.getInt(key, -1)
    }

    fun initialize(context: Context) {
        sharedPreferencesQuiz = context.getSharedPreferences(PREFS_NAME_QUIZ, Context.MODE_PRIVATE)
        sharedPreferencesQuestion = context.getSharedPreferences(PREFS_NAME_QUESTION, Context.MODE_PRIVATE)
    }

    private fun log(msg: String) {
        Logcat.log(msg, "SharedPreference", Logcat.LOG_FRAGMENT)
    }
}