package com.tpov.schoolquiz.presentation.custom

import java.util.*

object LanguageUtils {
    val languagesShortCodes = arrayOf("en", "ru", "fr", "de", "es")
    val languagesFullNames =
        arrayOf("Английский", "Русский", "Французский", "Немецкий", "Испанский")
    val ratingNum = arrayOf(0, 1, 2, 3)

    fun getLanguageShortCode(language: String): String {
        return when (language) {
            "Английский" -> "en"
            "Русский" -> "ru"
            "Французский" -> "fr"
            "Немецкий" -> "de"
            "Испанский" -> "es"
            else -> Locale.getDefault().language
        }
    }

    fun getLanguageFullName(shortCode: String): String {
        return when (shortCode) {
            "en" -> "Английский"
            "ru" -> "Русский"
            "fr" -> "Французский"
            "de" -> "Немецкий"
            "es" -> "Испанский"
            else -> "Неизвестный язык"
        }
    }

    fun getPositionLang(shortCode: String): Int {
        return when (shortCode.lowercase()) {
            "en" -> 0
            "ru" -> 1
            "fr" -> 2
            "de" -> 3
            "es" -> 4
            else -> 0
        }
    }
}
