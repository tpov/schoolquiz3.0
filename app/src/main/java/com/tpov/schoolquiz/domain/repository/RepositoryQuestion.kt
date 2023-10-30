package com.tpov.schoolquiz.domain.repository

import com.tpov.schoolquiz.data.database.entities.QuestionEntity

interface RepositoryQuestion {
    suspend fun getQuestionList(): List<QuestionEntity>

    fun getQuestionsByIdQuiz(idQuiz: Int): List<QuestionEntity>

    suspend fun insertQuestion(question: QuestionEntity)

    fun deleteQuestionByIdQuiz(idQuiz: Int)

    fun deleteQuestion(id: Int)
}