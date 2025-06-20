package com.tpov.common.presentation.utils

class DateUtil {
    fun getDateQuiz(): String {
        val unixTime = System.currentTimeMillis() / 1000
        return unixTime.toString()
    }
}