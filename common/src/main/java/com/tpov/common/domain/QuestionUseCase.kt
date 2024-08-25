package com.tpov.common.domain

import com.tpov.common.data.model.local.QuestionEntity
import com.tpov.common.domain.repository.RepositoryQuestion
import javax.inject.Inject

class QuestionUseCase @Inject constructor(private val repositoryQuestion: RepositoryQuestion) {
    suspend fun fetchQuestion(
        typeId: Int,
        categoryId: Int,
        subcategoryId: Int,
        pathLanguage: String,
        idQuiz: Int
    ) = repositoryQuestion.fetchQuestion(
        typeId,
        categoryId,
        subcategoryId,
        pathLanguage,
        idQuiz
    )

    suspend fun getQuestionByIdQuiz(idQuiz: Int) = repositoryQuestion.getQuestionByIdQuiz(idQuiz)

    suspend fun saveQuestion(questionEntity: QuestionEntity) {
        repositoryQuestion.saveQuestion(questionEntity)
    }

    suspend fun pushQuestion(questionEntity: QuestionEntity, pathLanguage: String, event: Int) {
        repositoryQuestion.pushQuestion(questionEntity, pathLanguage, event)
    }

    suspend fun updateQuestion(questionEntity: QuestionEntity) {
        repositoryQuestion.updateQuestion(questionEntity)
    }

    suspend fun deleteQuestionByIdQuiz(idQuiz: Int) {
        repositoryQuestion.deleteQuestionByIdQuiz(idQuiz)
    }

    suspend fun deleteRemoteQuestionByIdQuiz(idQuiz: Int, pathLanguage: String, typeId: Int, numQuestion: Int,hardQuestion: Boolean) {
        repositoryQuestion.deleteRemoteQuestionByIdQuiz(idQuiz, pathLanguage, typeId, numQuestion, hardQuestion)
    }

}