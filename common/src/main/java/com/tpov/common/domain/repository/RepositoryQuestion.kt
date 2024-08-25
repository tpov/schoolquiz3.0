package com.tpov.common.domain.repository

import com.tpov.common.data.model.local.QuestionEntity

interface RepositoryQuestion {
    suspend fun fetchQuestion(
        event: Int,
        categoryId: Int,
        subcategoryId: Int,
        pathLanguage: String,
        idQuiz: Int
    ): List<QuestionEntity>

    suspend fun getQuestionByIdQuiz(idQuiz: Int): List<QuestionEntity>
    suspend fun saveQuestion(questionEntity: QuestionEntity)
    suspend fun pushQuestion(questionEntity: QuestionEntity, pathLanguage: String, event: Int)
    suspend fun updateQuestion(questionEntity: QuestionEntity)
    suspend fun deleteQuestionByIdQuiz(idQuiz: Int)
    suspend fun deleteRemoteQuestionByIdQuiz(
        idQuiz: Int,
        pathLanguage: String,
        typeId: Int,
        numQuestion: Int,
        hardQuestion: Boolean)
}