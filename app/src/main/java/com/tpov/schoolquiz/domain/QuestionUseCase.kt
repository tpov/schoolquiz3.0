package com.tpov.schoolquiz.domain

import com.tpov.schoolquiz.data.database.entities.QuestionEntity
import com.tpov.schoolquiz.domain.repository.RepositoryQuestion
import javax.inject.Inject

class QuestionUseCase @Inject constructor(private val repositoryQuestion: RepositoryQuestion) {
    suspend fun getQuestionList() = repositoryQuestion.getQuestionList()

    fun getQuestionsByIdQuiz(idQuiz: Int) = repositoryQuestion.getQuestionsByIdQuiz(idQuiz)

    suspend fun insertQuestion(question: QuestionEntity) {
        repositoryQuestion.insertQuestion(question)
    }

    fun deleteQuestionByIdQuiz(idQuiz: Int) {
        repositoryQuestion.deleteQuestionByIdQuiz(idQuiz)
    }

    fun deleteQuestion(id: Int) {
        repositoryQuestion.deleteQuestion(id)
    }
}