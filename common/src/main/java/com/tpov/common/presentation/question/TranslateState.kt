package com.tpov.common.presentation.question

sealed class TranslateState {
    data object Initial : TranslateState()
    data object Loading : TranslateState()
    data class Success(val translatedText: String) : TranslateState()
    data class Error(val message: String) : TranslateState()
}