package com.tpov.common

import com.tpov.common.data.model.local.QuizEntity

class ErrorHandler(
    val onCloseScreen: () -> Unit,
    val onShowToast: (String) -> Unit,
    val interactor: Interactor
) {
    inline fun <reified T> handleError(
        message: String,
        interactorAction: () -> Unit
    ): T {
        onShowToast(message)
        interactorAction()
        interactor.sendErrorRemote()
        onCloseScreen()
        return getDefaultValue()
    }

    inline fun <reified T> getDefaultValue(): T = when (T::class) {
        Int::class -> -1 as T
        java.lang.Integer::class -> -1 as T
        String::class -> "" as T
        Boolean::class -> false as T
        java.lang.Boolean::class -> false as T
        Float::class -> -1f as T
        java.lang.Float::class -> -1f as T
        Double::class -> -1.0 as T
        java.lang.Double::class -> -1.0 as T
        Long::class -> -1L as T
        java.lang.Long::class -> -1L as T
        List::class -> emptyList<Any>() as T
        Set::class -> emptySet<Any>() as T
        Map::class -> emptyMap<Any, Any>() as T
        QuizEntity::class -> QuizEntity() as T
        Unit::class -> Unit as T
        else -> null as T
    }

    inline fun <reified T> notFoundQuiz(): T =
        handleError("notFoundQuiz") { interactor.notFoundQuiz() }

    inline fun <reified T> notFoundInputData(): T =
        handleError("Input data not found") { interactor.notFoundInputData() }

    inline fun <reified T> notFoundQuizValue(): T =
        handleError("Quiz value not found") { interactor.notFoundQuizValue() }

    inline fun <reified T> errorGetNumQuestion(): T =
        handleError("Error getting question number") { interactor.errorGetNumQuestion() }

    inline fun <reified T> notFoundNumberQuestionByTypeHardQuiz(): T =
        handleError("Questions not found for this quiz type") {
            interactor.notFoundNumberQuestionByTypeHardQuiz()
        }

    inline fun <reified T> notFoundInitTypeHardQuestion(): T =
        handleError("Quiz type not initialized") {
            interactor.notFoundInitTypeHardQuestion()
        }

    inline fun <reified T> notFountQuestionByLanguageUser(): T =
        handleError("Question not found for user language") {
            interactor.notFountQuestionByLanguageUser()
        }

    inline fun <reified T> errorTranslate(): T =
        handleError("Error in translation") { }
}