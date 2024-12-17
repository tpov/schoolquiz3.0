package com.tpov.common

import com.tpov.common.domain.usecase.QuestionDetailUseCase
import com.tpov.common.domain.usecase.QuestionUseCase
import com.tpov.common.domain.usecase.QuizUseCase
import com.tpov.common.domain.usecase.StructureUseCase
import javax.inject.Inject

class Interactor @Inject constructor(
    private val quizUseCase: QuizUseCase,
    private val questionUseCase: QuestionUseCase,
    private val questionDetailUseCase: QuestionDetailUseCase,
    private val structureUseCase: StructureUseCase
) {
    fun notFoundInputData() {

    }

    fun notFoundQuizValue() {
        TODO("Not yet implemented")
    }

    fun errorGetNumQuestion() {
        TODO("Not yet implemented")
    }

    fun notFoundNumberQuestionByTypeHardQuiz() {
        TODO("Not yet implemented")
    }

    fun notFoundInitTypeHardQuestion() {
        TODO("Not yet implemented")
    }

    fun handleQuizNotFound() {
        TODO("Not yet implemented")
    }

    fun handleInputDataNotFound() {
        TODO("Not yet implemented")
    }

    fun handleQuestionNotFound() {
        TODO("Not yet implemented")
    }

    fun sendErrorRemote() {
        TODO("Not yet implemented")
    }

    fun notFoundQuiz() {
        TODO("Not yet implemented")
    }

    fun notFountQuestionByLanguageUser() {
        TODO("Not yet implemented")
    }

}