package com.tpov.schoolquiz.presentation.custom

import android.content.Context
import android.content.SharedPreferences

object SharedPreferencesManager {
    private const val PREFS_NAME_QUIZ = "version_quiz"
    private const val PREFS_NAME_QUESTION = "version_question"
    private lateinit var sharedPreferencesQuiz: SharedPreferences
    private lateinit var sharedPreferencesQuestion: SharedPreferences
    fun setTimeMassage(time: String) {
        if (!::sharedPreferencesQuiz.isInitialized) {
            throw IllegalStateException("SharedPreferencesManager is not initialized")
        }
        val editor = sharedPreferencesQuiz.edit()
        editor.putString("massageTime", time)
        editor.apply()
    }
    fun getTimeMassage(): String {

        if (!::sharedPreferencesQuiz.isInitialized) {
            throw IllegalStateException("SharedPreferencesManager is not initialized")
        }
        return sharedPreferencesQuiz.getString("massageTime", "00:00") ?: "00:00"
    }
    fun setVersionQuiz(key: String, value: Int, context: Context) {
        val sharedPref = context.getSharedPreferences("profile", Context.MODE_PRIVATE)
        val tpovId = sharedPref?.getInt("tpovId", 0) ?: 0
        log("fun getVersionQuiz tpovId: $tpovId")
        log("fun setVersionQuiz key: $key|$tpovId, value: $value")

        if (!::sharedPreferencesQuiz.isInitialized) {
            throw IllegalStateException("SharedPreferencesManager is not initialized")
        }
        val editor = sharedPreferencesQuiz.edit()
        editor.putInt("$key|$tpovId", value)
        editor.apply()
    }

    fun getVersionQuiz(key: String, context: Context): Int {
        val sharedPref = context.getSharedPreferences("profile", Context.MODE_PRIVATE)
        val tpovId = sharedPref?.getInt("tpovId", 0) ?: 0
        log("fun getVersionQuiz tpovId: $tpovId")
        log("fun getVersionQuiz key: $key|$tpovId, value: ${sharedPreferencesQuiz.getInt("$key|$tpovId", -1)}")

        if (!::sharedPreferencesQuiz.isInitialized) {
            throw IllegalStateException("SharedPreferencesManager is not initialized")
        }
        return sharedPreferencesQuiz.getInt("$key|$tpovId", -1)
    }

    fun initialize(context: Context) {
        sharedPreferencesQuiz = context.getSharedPreferences(PREFS_NAME_QUIZ, Context.MODE_PRIVATE)
        sharedPreferencesQuestion = context.getSharedPreferences(PREFS_NAME_QUESTION, Context.MODE_PRIVATE)
    }

    private fun log(msg: String) {
        Logcat.log(msg, "SharedPreference", Logcat.LOG_FRAGMENT)
    }
}