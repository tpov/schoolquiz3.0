package com.tpov.common.domain.usecase

import com.tpov.common.data.model.local.QuestionDetailLocal
import com.tpov.common.domain.repository.RepositoryQuestionDetail
import com.tpov.common.presentation.model.PathStructure
import javax.inject.Inject

class QuestionDetailUseCase @Inject constructor(private val repositoryQuestionDetail: RepositoryQuestionDetail) {

    suspend fun fetchQuestionDetail(
        pathStructure: PathStructure
    ) = repositoryQuestionDetail.fetchQuestionDetails(pathStructure)

    suspend fun pushQuestionDetail(questionDetailLocal: QuestionDetailLocal) {
        repositoryQuestionDetail.pushQuestionDetails(questionDetailLocal)
    }

    suspend fun getQuestionDetailByPath(pathStructure: PathStructure) =
        repositoryQuestionDetail.getQuestionDetailByPath(pathStructure)

    suspend fun saveQuestionDetail(questionDetailLocal: QuestionDetailLocal) {
        repositoryQuestionDetail.saveQuestionDetail(questionDetailLocal)
    }

    suspend fun updateQuestionDetail(questionDetailLocal: QuestionDetailLocal) {
        repositoryQuestionDetail.updateQuestionDetail(questionDetailLocal)
    }

    suspend fun deleteQuestionDetailById(idQuiz: Int) {
        repositoryQuestionDetail.deleteQuestionDetailById(idQuiz)
    }

    suspend fun deleteRemoteQuestionDetailByPath(pathStructure: PathStructure) {
        repositoryQuestionDetail.deleteRemoteQuestionDetailByPath(pathStructure)
    }

}
