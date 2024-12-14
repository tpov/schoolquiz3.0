package com.tpov.common.presentation

import com.tpov.common.data.model.local.QuizEntity

abstract class Errors {
    fun errorGetNumQuestion(): Int {
        closeActivity()
        return 0
    }

    fun notFountQuestionByLanguageUser() {

    }

    fun notFoundQuiz(): QuizEntity {
        return QuizEntity()
    }

    fun notFoundInputData(): Int {
        return 0
    }

    fun notFoundQuizValue(): Int {

        return 0
    }

    fun notFoundNumberQuestionByTypeHardQuiz(): Int {

        return 0
    }

    fun notFoundInitTypeHardQuestion(): Boolean {

        return false
    }

    abstract fun closeActivity()
}