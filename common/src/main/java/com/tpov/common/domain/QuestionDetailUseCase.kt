package com.tpov.common.domain

import com.tpov.common.data.model.local.QuestionDetailEntity
import com.tpov.common.domain.repository.RepositoryQuestionDetail
import javax.inject.Inject

class QuestionDetailUseCase @Inject constructor(private val repositoryQuestionDetail: RepositoryQuestionDetail) {

    suspend fun fetchQuestionDetail(
        typeId: Int,
        categoryId: String,
        subcategoryId: String,
        subsubcategoryId: String,
        idQuiz: Int
    ) = repositoryQuestionDetail
        .fetchQuestionDetail(typeId, categoryId, subcategoryId, subsubcategoryId, idQuiz)

    suspend fun pushQuestion(
        event: Int, categoryId: String, subcategoryId: String,
        subsubcategoryId: String, idQuiz: Int,
        questionDetailEntity: QuestionDetailEntity
    ) {
        repositoryQuestionDetail.pushQuestionDetail(
            questionDetailEntity.id!!,
            event,
            categoryId,
            subcategoryId,
            subsubcategoryId,
            idQuiz,
            questionDetailEntity.toQuestionDetail()
        )
    }

    suspend fun getQuestionDetailByIdQuiz(idQuiz: Int) =
        repositoryQuestionDetail.getQuestionDetailByIdQuiz(idQuiz)

    suspend fun saveQuestionDetail(questionDetailEntity: QuestionDetailEntity) {
        repositoryQuestionDetail.saveQuestionDetail(questionDetailEntity)
    }

    suspend fun updateQuestionDetail(questionDetailEntity: QuestionDetailEntity) {
        repositoryQuestionDetail.updateQuestionDetail(questionDetailEntity)
    }

    suspend fun deleteQuestionDetailById(idQuiz: Int) {
        repositoryQuestionDetail.deleteQuestionDetailById(idQuiz)
    }

    suspend fun deleteRemoteQuestionDetailByIdQuiz(idQuiz: Int, typeId: Int) {
        repositoryQuestionDetail.deleteRemoteQuestionDetailByIdQuiz(idQuiz, typeId)
    }

}