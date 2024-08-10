package com.tpov.common.domain.repository

import com.tpov.common.data.model.local.QuestionDetailEntity
import com.tpov.common.data.model.remote.QuestionDetailRemote

interface RepositoryQuestionDetail {
    suspend fun fetchQuestionDetail(
        typeId: Int, categoryId: String, subcategoryId: String,
        subsubcategoryId: String, idQuiz: Int
    ): List<QuestionDetailRemote>
    suspend fun pushQuestionDetail(
        id: Int,
        event: Int,
        categoryId: String,
        subcategoryId: String,
        subsubcategoryId: String,
        idQuiz: Int,
        questionDetailRemote: QuestionDetailRemote
    )
    suspend fun getQuestionDetailByIdQuiz(idQuiz: Int): List<QuestionDetailEntity>?
    suspend fun saveQuestionDetail(questionDetailEntity: QuestionDetailEntity)
    suspend fun updateQuestionDetail(questionDetailEntity: QuestionDetailEntity)
    suspend fun deleteQuestionDetailById(id: Int)
    suspend fun deleteRemoteQuestionDetailByIdQuiz(idQuiz: Int, typeId: Int)
}