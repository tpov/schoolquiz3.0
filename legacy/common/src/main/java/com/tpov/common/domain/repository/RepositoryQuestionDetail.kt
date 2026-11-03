package com.tpov.common.domain.repository

import com.tpov.common.data.model.local.QuestionDetailLocal
import com.tpov.common.presentation.model.PathStructure

interface RepositoryQuestionDetail {
    suspend fun fetchQuestionDetails(
        pathStructure: PathStructure
    ): List<QuestionDetailLocal>
    suspend fun pushQuestionDetails(
        questionDetailLocal: QuestionDetailLocal
    )
    suspend fun getQuestionDetailByPath(pathStructure: PathStructure): List<QuestionDetailLocal>?
    suspend fun saveQuestionDetail(questionDetailLocal: QuestionDetailLocal)
    suspend fun updateQuestionDetail(questionDetailLocal: QuestionDetailLocal)
    suspend fun deleteQuestionDetailById(id: Int)
    suspend fun deleteRemoteQuestionDetailByPath(pathStructure: PathStructure)
}
