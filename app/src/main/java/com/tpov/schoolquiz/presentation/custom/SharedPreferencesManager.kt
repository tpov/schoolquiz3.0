package com.tpov.schoolquiz.presentation.custom

import android.content.Context
import android.content.SharedPreferences

object SharedPreferencesManager {
    private const val PREFS_NAME_QUIZ = "version_quiz"
    private const val PREFS_NAME_QUESTION = "version_question"
    private const val PREFS_NAME_COUNTS = "profile_counts"
    private const val PREFS_TPOV_ID = "profile"
    private const val PREFS_COUNT_START_APP = "count_start_app"
    private lateinit var sharedPreferencesQuiz: SharedPreferences
    private lateinit var sharedPreferencesQuestion: SharedPreferences
    private lateinit var sharedPreferencesCounts: SharedPreferences
    private lateinit var sharedPreferencesTpovId: SharedPreferences
    private lateinit var sharedPreferencesCountStartApp: SharedPreferences
    var updateProfile = true

    fun initialize(context: Context) {
        sharedPreferencesQuiz = context.getSharedPreferences(PREFS_NAME_QUIZ, Context.MODE_PRIVATE)
        sharedPreferencesQuestion =
            context.getSharedPreferences(PREFS_NAME_QUESTION, Context.MODE_PRIVATE)
        sharedPreferencesCounts =
            context.getSharedPreferences(PREFS_NAME_COUNTS, Context.MODE_PRIVATE)
        sharedPreferencesTpovId =
            context.getSharedPreferences(PREFS_TPOV_ID, Context.MODE_PRIVATE)
        sharedPreferencesCountStartApp =
            context.getSharedPreferences(PREFS_COUNT_START_APP, Context.MODE_PRIVATE)
    }

    fun getTpovId(): Int {
        if (!SharedPreferencesManager::sharedPreferencesTpovId.isInitialized) {
            throw IllegalStateException("SharedPreferencesManager is not initialized")
        }
        return sharedPreferencesTpovId.getInt("tpovId", -1)
    }

    fun setTpovId(tpovId: Int) {
        if (!SharedPreferencesManager::sharedPreferencesTpovId.isInitialized) {
            throw IllegalStateException("SharedPreferencesManager is not initialized")
        }
        val editor = sharedPreferencesTpovId.edit()
        editor.putInt("tpovId", tpovId)
        editor.apply()
    }

    fun getCountStartApp(): Int {
        if (!SharedPreferencesManager::sharedPreferencesCountStartApp.isInitialized) {
            throw IllegalStateException("SharedPreferencesManager is not initialized")
        }
        return sharedPreferencesCountStartApp.getInt("countStartApp", 0)
    }

    fun setCountStartApp(count: Int) {
        if (!SharedPreferencesManager::sharedPreferencesCountStartApp.isInitialized) {
            throw IllegalStateException("SharedPreferencesManager is not initialized")
        }
        val editor = sharedPreferencesCountStartApp.edit()
        editor.putInt("countStartApp", count)
        editor.apply()
    }

    fun getCountTimeInGame(): Int {
        if (!SharedPreferencesManager::sharedPreferencesCountStartApp.isInitialized) {
            throw IllegalStateException("SharedPreferencesManager is not initialized")
        }
        return sharedPreferencesCountStartApp.getInt("countTimeInGame", 0)
    }

    fun setCountTimeInGame(time: Int) {
        if (!SharedPreferencesManager::sharedPreferencesCountStartApp.isInitialized) {
            throw IllegalStateException("SharedPreferencesManager is not initialized")
        }
        val editor = sharedPreferencesCountStartApp.edit()
        editor.putInt("countTimeInGame", time)
        editor.apply()
    }

    fun setTimeMassage(time: String) {
        if (!SharedPreferencesManager::sharedPreferencesQuiz.isInitialized) {
            throw IllegalStateException("SharedPreferencesManager is not initialized")
        }
        val editor = sharedPreferencesQuiz.edit()
        editor.putString("massageTime", time)
        editor.apply()
    }

    fun getTimeMassage(): String {

        if (!SharedPreferencesManager::sharedPreferencesQuiz.isInitialized) {
            throw IllegalStateException("SharedPreferencesManager is not initialized")
        }
        return sharedPreferencesQuiz.getString("massageTime", "0") ?: "0"
    }

    fun setVersionQuiz(key: String, value: Int) {
        val tpovId = getTpovId()
        log("fun getVersionQuiz tpovId: $tpovId")
        log("fun setVersionQuiz key: $key|$tpovId, value: $value")

        if (!SharedPreferencesManager::sharedPreferencesQuiz.isInitialized) {
            throw IllegalStateException("SharedPreferencesManager is not initialized")
        }
        val editor = sharedPreferencesQuiz.edit()
        editor.putInt("$key|$tpovId", value)
        editor.apply()
    }

    fun getVersionQuiz(key: String): Int {
        val tpovId = getTpovId()
        log("fun getVersionQuiz tpovId: $tpovId")
        log(
            "fun getVersionQuiz key: $key|$tpovId, value: ${
                sharedPreferencesQuiz.getInt(
                    "$key|$tpovId",
                    -1
                )
            }"
        )

        if (!SharedPreferencesManager::sharedPreferencesQuiz.isInitialized) {
            throw IllegalStateException("SharedPreferencesManager is not initialized")
        }
        return sharedPreferencesQuiz.getInt("$key|$tpovId", -1)
    }

    fun addCountSendMassage() {

        if (!SharedPreferencesManager::sharedPreferencesQuiz.isInitialized) {
            throw IllegalStateException("SharedPreferencesManager is not initialized")
        }

        val editor = sharedPreferencesQuiz.edit()
        editor.putInt(
            "countMassage|${getTpovId()}",
            sharedPreferencesQuiz.getInt("countMassage|${getTpovId()}", 0) + 1
        )
        editor.apply()
    }

    fun getCountMassageIdAndReset(): Int {
        val tpovId = getTpovId()

        if (!SharedPreferencesManager::sharedPreferencesQuiz.isInitialized) {
            throw IllegalStateException("SharedPreferencesManager is not initialized")
        }

        val count = sharedPreferencesQuiz.getInt("countMassage|$tpovId", 0)

        val editor = sharedPreferencesQuiz.edit()
        editor.putInt(
            "countMassage|${getTpovId()}",
            0
        )

        return count
    }

    fun setProfile(
        skill: Int,
        nolic: Int,
        gold: Int,
        premium: String,
        nick: String
    ) {
        log("skill: $skill")
        log("nolic: $nolic")
        log("gold: $gold")
        log("premium: $premium")
        log("nick: $nick")

        val editor = sharedPreferencesCounts.edit()
        editor.putInt("countSkill", skill)
        editor.putInt("countGold", gold)
        editor.putString("countPremium", premium)
        editor.putString("countNick", nick)
        editor.putInt("countNolic", nolic)
        editor.apply()
    }

    fun getSkill(): Int {
        if (!SharedPreferencesManager::sharedPreferencesCounts.isInitialized) {
            throw IllegalStateException("SharedPreferencesManager is not initialized")
        }
        return sharedPreferencesCounts.getInt("countSkill", 0)
    }

    fun getGold(): Int {
        if (!SharedPreferencesManager::sharedPreferencesCounts.isInitialized) {
            throw IllegalStateException("SharedPreferencesManager is not initialized")
        }
        return sharedPreferencesCounts.getInt("countGold", 0)
    }

    fun getPremium(): String {
        if (!SharedPreferencesManager::sharedPreferencesCounts.isInitialized) {
            throw IllegalStateException("SharedPreferencesManager is not initialized")
        }
        return sharedPreferencesCounts.getString("countPremium", "") ?: ""
    }

    fun getNick(): String {
        if (!SharedPreferencesManager::sharedPreferencesCounts.isInitialized) {
            throw IllegalStateException("SharedPreferencesManager is not initialized")
        }
        return sharedPreferencesCounts.getString("countNick", "") ?: ""
    }

    fun getNolic(): Int {
        if (!SharedPreferencesManager::sharedPreferencesCounts.isInitialized) {
            throw IllegalStateException("SharedPreferencesManager is not initialized")
        }
        return sharedPreferencesCounts.getInt("countNolic", 0)
    }

    private fun log(msg: String) {
        Logcat.log(msg, "SharedPreference", Logcat.LOG_FRAGMENT)
    }
}