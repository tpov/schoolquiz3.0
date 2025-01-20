package com.tpov.common.domain.repository

import com.tpov.common.data.model.local.QuestionDetailEntity
import com.tpov.common.presentation.model.PathStructure

interface RepositoryQuestionDetail {
    suspend fun fetchQuestionDetails(
        pathStructure: PathStructure
    ): List<QuestionDetailEntity>
    suspend fun pushQuestionDetails(
       questionDetailEntity: QuestionDetailEntity
    )
    suspend fun getQuestionDetailByPath(pathStructure: PathStructure): List<QuestionDetailEntity>?
    suspend fun saveQuestionDetail(questionDetailEntity: QuestionDetailEntity)
    suspend fun updateQuestionDetail(questionDetailEntity: QuestionDetailEntity)
    suspend fun deleteQuestionDetailById(id: Int)
    suspend fun deleteRemoteQuestionDetailByPath(pathStructure: PathStructure)
}