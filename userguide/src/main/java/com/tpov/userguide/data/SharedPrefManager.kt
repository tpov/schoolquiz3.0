package com.tpov.userguide.data

import android.content.Context

internal object SharedPrefManager {
    private const val PREF_KEY = "userguide_key"
    private const val PREF_KEY_COUNTER_VIEW = "userguide_key_view"

    fun setExactMatchKey(context: Context, value: Int, idGroupGuide: Int) {
        val sharedPreferences = context.getSharedPreferences(PREF_KEY, Context.MODE_PRIVATE)
        sharedPreferences.edit().putInt(idGroupGuide.toString(), value).apply()
    }

    fun getExactMatchKey(context: Context, idGroupGuide: Int): Int? {
        val sharedPreferences = context.getSharedPreferences(PREF_KEY, Context.MODE_PRIVATE)
        return if (sharedPreferences.contains(idGroupGuide.toString())) {
            sharedPreferences.getInt(idGroupGuide.toString(), 0)
        } else {
            null
        }
    }

    fun setMinValueKey(context: Context, value: Int, idGroupGuide: Int) {
        val sharedPreferences = context.getSharedPreferences(PREF_KEY, Context.MODE_PRIVATE)
        val currentValue = sharedPreferences.getInt(idGroupGuide.toString(), Int.MAX_VALUE)
        if (value < currentValue) {
            sharedPreferences.edit().putInt(idGroupGuide.toString(), value).apply()
        }
    }

    fun getMinValueKey(context: Context, idGroupGuide: Int): Int? {
        val sharedPreferences = context.getSharedPreferences(PREF_KEY, Context.MODE_PRIVATE)
        return if (sharedPreferences.contains(idGroupGuide.toString())) {
            sharedPreferences.getInt(idGroupGuide.toString(), 0)
        } else {
            null
        }
    }

    fun getCounterView(context: Context, idView: Int): Int {
        val sharedPreferences = context.getSharedPreferences(PREF_KEY_COUNTER_VIEW, Context.MODE_PRIVATE)
        return sharedPreferences.getInt(idView.toString(), 0)
    }

    fun incrementCounterDialogView(context: Context, idView: Int) {
        val sharedPreferences = context.getSharedPreferences(PREF_KEY_COUNTER_VIEW, Context.MODE_PRIVATE)
        val counter = sharedPreferences.getInt(idView.toString(), 0)
        val updatedCounter = counter + 1
        sharedPreferences.edit().putInt(idView.toString(), updatedCounter).apply()
    }
}