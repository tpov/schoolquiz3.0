package com.tpov.userguide.domain

import android.content.Context
import com.tpov.userguide.data.SharedPrefManager

class UserGuideUseCase(private val context: Context) {

    fun setExactMatchKey(value: Int, idGroupGuide: Int) {
        SharedPrefManager.setExactMatchKey(context, value, idGroupGuide)
    }

    fun getExactMatchKey(idGroupGuide: Int) = SharedPrefManager.getExactMatchKey(context, idGroupGuide)

    fun setMinValueKey(value: Int, idGroupGuide: Int) {
        SharedPrefManager.setMinValueKey(context, value, idGroupGuide)
    }

    fun getMinValueKey(idGroupGuide: Int) = SharedPrefManager.getMinValueKey(context, idGroupGuide)

    fun getCountRepeat(idView: Int) = SharedPrefManager.getCounterView(context, idView)

    fun incrementCounterDialogView(idView: Int) {
        SharedPrefManager.incrementCounterDialogView(context, idView)
    }
}