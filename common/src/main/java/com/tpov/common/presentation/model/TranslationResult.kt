package com.tpov.common.presentation.model

data class TranslationResult(
    val originalText: String,
    val translatedText: String,
    val detectedLanguage: String
)