package com.tpov.common.domain.repository

import com.tpov.common.data.model.local.QuestionEntity
import com.tpov.common.data.model.remote.Question

interface RepositoryQuestion {
    suspend fun fetchQuestion(
        typeId: Int,
        categoryId: String,
        subcategoryId: String,
        pathLanguage: String,
        idQuiz: Int
    ): List<Question>

    suspend fun getQuestionByIdQuiz(idQuiz: Int): List<QuestionEntity>
    suspend fun saveQuestion(questionEntity: QuestionEntity)
    suspend fun updateQuestion(questionEntity: QuestionEntity)
    suspend fun deleteQuestionByIdQuiz(idQuiz: Int)
    suspend fun deleteRemoteQuestionByIdQuiz(idQuiz: Int, pathLanguage: String, typeId: Int)
}