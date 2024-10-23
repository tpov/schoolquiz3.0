package com.tpov.common.presentation.utils

class DataUtil {
    fun getDataQuiz(): String {
        val unixTime = System.currentTimeMillis() / 1000
        return unixTime.toString()
    }
}