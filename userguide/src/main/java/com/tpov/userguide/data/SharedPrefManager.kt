package com.tpov.userguide.data

import android.content.Context

internal object SharedPrefManager {
    private const val PREF_KEY = "userguide_key"
    private const val PREF_KEY_COUNTER_VIEW = "userguide_key_view"


    fun getCounterView(context: Context, hash: Int): Int {
        val sharedPreferences = context.getSharedPreferences(PREF_KEY_COUNTER_VIEW, Context.MODE_PRIVATE)
        return sharedPreferences.getInt(hash.toString(), 0)
    }

    fun incrementCounterDialogView(context: Context, hash: Int) {
        val sharedPreferences = context.getSharedPreferences(PREF_KEY_COUNTER_VIEW, Context.MODE_PRIVATE)
        val counter = sharedPreferences.getInt(hash.toString(), 0)
        val updatedCounter = counter + 1
        sharedPreferences.edit().putInt(hash.toString(), updatedCounter).apply()
    }
}