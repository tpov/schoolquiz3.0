package com.tpov.schoolquiz.presentation.custom

import java.util.Locale

object LanguageUtils {
    val languagesShortCodes = arrayOf("en", "ru", "fr", "de", "es")
    val languagesFullNames =
        arrayOf("Английский", "Русский", "Французский", "Немецкий", "Испанский")

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
}
