package com.tpov.common.presentation.utils

class DateUtil {
    fun getDateQuiz(): String {
        val unixTime = System.currentTimeMillis() / 1000
        return unixTime.toString()
    }

    fun getUnixDay(): Long {
        return System.currentTimeMillis() / (1000 * 60 * 60 * 24)
    }

    fun getUnixDayString(): String {
        return getUnixDay().toString()
    }
}