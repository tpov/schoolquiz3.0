package com.tpov.common.domain.usecase

import com.tpov.common.data.model.local.QuestionDetailEntity
import com.tpov.common.domain.repository.RepositoryQuestionDetail
import com.tpov.common.presentation.model.PathStructure
import javax.inject.Inject

class QuestionDetailUseCase @Inject constructor(private val repositoryQuestionDetail: RepositoryQuestionDetail) {

    suspend fun fetchQuestionDetail(
        pathStructure: PathStructure
    ) = repositoryQuestionDetail.fetchQuestionDetails(pathStructure)

    suspend fun pushQuestion(questionDetailEntity: QuestionDetailEntity) {
        repositoryQuestionDetail.pushQuestionDetails(questionDetailEntity)
    }

    suspend fun getQuestionDetailByPath(pathStructure: PathStructure) =
        repositoryQuestionDetail.getQuestionDetailByPath(pathStructure)

    suspend fun saveQuestionDetail(questionDetailEntity: QuestionDetailEntity) {
        repositoryQuestionDetail.saveQuestionDetail(questionDetailEntity)
    }

    suspend fun updateQuestionDetail(questionDetailEntity: QuestionDetailEntity) {
        repositoryQuestionDetail.updateQuestionDetail(questionDetailEntity)
    }

    suspend fun deleteQuestionDetailById(idQuiz: Int) {
        repositoryQuestionDetail.deleteQuestionDetailById(idQuiz)
    }

    suspend fun deleteRemoteQuestionDetailByPath(pathStructure: PathStructure) {
        repositoryQuestionDetail.deleteRemoteQuestionDetailByPath(pathStructure)
    }

}