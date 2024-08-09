package com.tpov.common.domain.repository

import com.tpov.common.data.model.local.QuizEntity
import com.tpov.common.data.model.remote.QuizRemote

interface RepositoryQuiz {
    suspend fun fetchQuizzes(typeId: Int, categoryId: Int, subcategoryId: Int, subsubcategoryId: Int): List<QuizRemote>
    suspend fun getQuizzes(): List<QuizEntity>?
    suspend fun saveQuiz(quiz: QuizEntity)
    suspend fun pushQuiz(quizRemote: QuizRemote, idQuiz: Int, categoryId: Int, subcategoryId: Int, subsubcategoryId: Int)
    suspend fun insertQuiz(quiz: QuizEntity)
    suspend fun deleteQuizById(idQuiz: Int)
    suspend fun deleteRemoteQuizById(quizRemote: QuizRemote, idQuiz: Int, categoryId: Int, subcategoryId: Int, subsubcategoryId: Int)
}