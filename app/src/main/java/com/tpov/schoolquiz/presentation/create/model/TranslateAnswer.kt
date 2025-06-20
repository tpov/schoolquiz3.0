package com.tpov.schoolquiz.presentation.create.model

import com.tpov.common.presentation.utils.LanguageUtils

data class TranslateAnswer(
    val listAnswer: MutableList<String>,
    val language: LanguageUtils
)
