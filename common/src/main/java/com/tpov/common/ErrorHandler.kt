package com.tpov.common

import com.tpov.common.data.model.local.QuizEntity

class ErrorHandler(
    private val onCloseScreen: () -> Unit,
    private val onShowToast: (String) -> Unit,
    private val interactor: Interactor
) {

    fun notFoundQuiz(): QuizEntity {
        onShowToast("notFoundQuiz")
        interactor.notFoundQuiz()
        interactor.sendErrorRemote()
        onCloseScreen()
        return QuizEntity()
    }

    fun notFoundInputData(): Int {
        onShowToast("Input data not found")
        interactor.notFoundInputData()
        interactor.sendErrorRemote()
        onCloseScreen()
        return 0
    }

    fun notFoundQuizValue(): Int {
        onShowToast("Quiz value not found")
        interactor.notFoundQuizValue()
        interactor.sendErrorRemote()
        onCloseScreen()
        return 0
    }

    fun errorGetNumQuestion(): Int {
        onShowToast("Error getting question number")
        interactor.errorGetNumQuestion()
        interactor.sendErrorRemote()
        onCloseScreen()
        return 0
    }

    fun notFoundNumberQuestionByTypeHardQuiz(): Int {
        onShowToast("Questions not found for this quiz type")
        interactor.notFoundNumberQuestionByTypeHardQuiz()
        interactor.sendErrorRemote()
        onCloseScreen()
        return 0
    }

    fun notFoundInitTypeHardQuestion(): Boolean {
        onShowToast("Quiz type not initialized")
        interactor.notFoundInitTypeHardQuestion()
        interactor.sendErrorRemote()
        onCloseScreen()
        return false
    }

    private fun handleQuizNotFound(): Int {
        onShowToast("handleQuizNotFound")
        interactor.handleQuizNotFound()
        interactor.sendErrorRemote()
        onCloseScreen()
        return 0
    }

    private fun handleInputDataNotFound(): Int {
        onShowToast("handleInputDataNotFound")
        interactor.handleInputDataNotFound()
        interactor.sendErrorRemote()
        onCloseScreen()
        return 0
    }

    private fun handleQuestionNotFound(): Int {
        onShowToast("handleQuestionNotFound")
        interactor.handleQuestionNotFound()
        interactor.sendErrorRemote()
        onCloseScreen()
        return 0
    }

    fun notFountQuestionByLanguageUser() {
        onShowToast("handleQuestionNotFound")
        interactor.notFountQuestionByLanguageUser()
        interactor.sendErrorRemote()
        onCloseScreen()
    }
}