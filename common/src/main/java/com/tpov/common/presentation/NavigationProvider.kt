package com.tpov.common.presentation

interface NavigationProvider {
    fun openQuestionActivity(idQuiz: Int, typeQuestion: Boolean)
}