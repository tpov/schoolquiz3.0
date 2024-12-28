package com.tpov.userguide.domain

import android.content.Context
import com.tpov.userguide.data.SharedPrefManager

class UserGuideUseCase(private val context: Context) {

    fun getCountRepeat(hashcode: Int) = SharedPrefManager.getCounterView(context, hashcode)

    fun incrementCounterDialogView(hashcode: Int) {
        SharedPrefManager.incrementCounterDialogView(context, hashcode)
    }
}