package com.tpov.common.presentation

import com.tpov.common.ExceptionHandler
import com.tpov.common.ExceptionInteractor

class PresentationExceptions(
    beforeException: (String) -> Unit,
    afterException: () -> Unit,
    interactor: ExceptionInteractor
) : ExceptionHandler(
    beforeException, afterException , interactor
) {
    inline fun <reified T> notFoundQuiz(): T =
        handleException("notFoundQuiz") { interactor.notFoundQuiz() }

    inline fun <reified T> notFoundInputData(): T =
        handleException("Input data not found") { interactor.notFoundInputData() }
    inline fun <reified T> notFoundPath(): T =
        handleException("Input data not found") { interactor.notFoundInputData() }

    inline fun <reified T> notFoundQuizValue(): T =
        handleException("Quiz value not found") { interactor.notFoundQuizValue() }

    inline fun <reified T> errorGetNumQuestion(): T =
        handleException("Error getting question number") { interactor.errorGetNumQuestion() }

    inline fun <reified T> notFoundNumberQuestionByTypeHardQuiz(): T =
        handleException("Questions not found for this quiz type") {
            interactor.notFoundNumberQuestionByTypeHardQuiz()
        }

    inline fun <reified T> notFoundInitTypeHardQuestion(): T =
        handleException("Quiz type not initialized") {
            interactor.notFoundInitTypeHardQuestion()
        }

    inline fun <reified T> notFountQuestionByLanguageUser(): T =
        handleException("Question not found for user language") {
            interactor.notFountQuestionByLanguageUser()
        }

    inline fun <reified T> errorTranslate(): T =
        handleException("Error in translation") { }


}
