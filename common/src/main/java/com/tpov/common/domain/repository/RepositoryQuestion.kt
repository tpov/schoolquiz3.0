package com.tpov.common.domain.repository

import com.tpov.common.data.model.local.QuestionEntity
import com.tpov.common.data.model.remote.QuestionRemote

interface RepositoryQuestion {
    suspend fun fetchQuestion(
        event: Int,
        categoryId: Int,
        subcategoryId: Int,
        pathLanguage: String,
        idQuiz: Int
    ): List<QuestionRemote>

    suspend fun getQuestionByIdQuiz(idQuiz: Int): List<QuestionEntity>
    suspend fun saveQuestion(questionEntity: QuestionEntity)
    suspend fun pushQuestion(questionEntity: QuestionRemote, event: Int,idQuiz: Int)
    suspend fun updateQuestion(questionEntity: QuestionEntity)
    suspend fun deleteQuestionByIdQuiz(idQuiz: Int)
    suspend fun deleteRemoteQuestionByIdQuiz(
        idQuiz: Int,
        typeId: Int,)
}