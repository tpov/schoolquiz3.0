package com.tpov.common.domain.repository

import com.tpov.common.data.model.local.QuizEntity

interface RepositoryQuiz {
    suspend fun fetchQuizzes(typeId: Int, categoryId: String, subcategoryId: String, subsubcategoryId: String): List<com.tpov.common.data.model.remote.Quiz>
    suspend fun getQuizzes(): List<QuizEntity>?
    suspend fun saveQuiz(quiz: QuizEntity)
    suspend fun pushQuiz(quiz: QuizEntity)
    suspend fun insertQuiz(quiz: QuizEntity)
    suspend fun deleteQuizById(idQuiz: Int)
    suspend fun deleteRemoteQuizById(quiz: QuizEntity)
}
