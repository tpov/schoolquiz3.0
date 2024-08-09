package com.tpov.common.domain.repository

import com.tpov.common.data.model.local.QuizEntity
import com.tpov.common.data.model.remote.Quiz

interface RepositoryQuiz {
    suspend fun fetchQuizzes(typeId: Int, categoryId: Int, subcategoryId: Int, subsubcategoryId: Int): List<Quiz>
    suspend fun getQuizzes(): List<QuizEntity>?
    suspend fun saveQuiz(quiz: QuizEntity)
    suspend fun pushQuiz(quiz: Quiz, idQuiz: Int, categoryId: Int, subcategoryId: Int, subsubcategoryId: Int)
    suspend fun insertQuiz(quiz: QuizEntity)
    suspend fun deleteQuizById(idQuiz: Int)
    suspend fun deleteRemoteQuizById(quiz: Quiz, idQuiz: Int, categoryId: Int, subcategoryId: Int, subsubcategoryId: Int)
}