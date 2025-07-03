package com.tpov.schoolquiz.data.fierbase

data class TranslateConfigRemote(
    val languagesFreeTranslate: ArrayList<String>,
    val languagesGoogleTranslate: ArrayList<String>,
    val leftSymbolTranslate: Int,
    val lvlFreeTranslator: Int,
    val lvlGoogleTranslator: Int,
)
