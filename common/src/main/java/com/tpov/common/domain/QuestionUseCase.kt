package com.tpov.common.domain

import android.util.Log
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
    suspend fun getQuestionByIdQuiz(idQuiz: Int): ArrayList<QuestionEntity> {
        val questionList = repositoryQuestion.getQuestionByIdQuiz(idQuiz)
        return ArrayList(questionList)
    }

    suspend fun insertQuestion(questionEntity: QuestionEntity) {
        Log.d("insertQuestion", "$questionEntity")
        repositoryQuestion.saveQuestion(questionEntity)
    }

    suspend fun pushQuestion(questionEntity: QuestionEntity, event: Int, idQuiz: Int) {
        repositoryQuestion.pushQuestion(questionEntity.toQuestionRemote(), event, questionEntity.idQuiz)
    }

    suspend fun updateQuestion(questionEntity: QuestionEntity) {
        repositoryQuestion.updateQuestion(questionEntity)
    }

    suspend fun deleteQuestionByIdQuiz(idQuiz: Int) {
        repositoryQuestion.deleteQuestionByIdQuiz(idQuiz)
    }

    suspend fun deleteRemoteQuestionByIdQuiz(
        idQuiz: Int,
        typeId: Int
    ) {
        repositoryQuestion.deleteRemoteQuestionByIdQuiz(idQuiz, typeId)
    }

}