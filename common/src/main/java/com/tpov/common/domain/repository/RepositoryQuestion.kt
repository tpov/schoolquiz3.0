package com.tpov.common.domain.repository

import com.tpov.common.data.model.local.QuestionEntity
import com.tpov.common.data.model.remote.QuestionRemote

interface RepositoryQuestion {
    suspend fun getAllMustTrnslLangsPaidQuestions(): Set<String>

    suspend fun fetchQuestion(
        event: Int,
        categoryId: Int,
        subcategoryId: Int,
        pathLanguage: String,
        idQuiz: Int
    ): List<QuestionRemote>

    suspend fun getQuestionByIdQuiz(idQuiz: Int): List<QuestionEntity>
    suspend fun saveQuestion(questionEntity: QuestionEntity)
    suspend fun pushQuestion(
        questionEntity: QuestionRemote,
        event: Int,
        idQuiz: Int
    )

    suspend fun updateQuestion(questionEntity: QuestionEntity)
    suspend fun deleteQuestionByIdQuiz(idQuiz: Int)
    suspend fun deleteRemoteQuestionByIdQuiz(idQuiz: Int, typeId: Int)
    suspend fun pushQuestionForTranslate(
        question: QuestionRemote,
        idQuiz: Int,
        usePaidTranslation: Boolean,
        toLang: String
    )

    suspend fun remoteLangsQuestions(hardQuestion: Boolean, numQuestion: Int, idQuiz: Int, eventQuiz: Int): List<String>
    suspend fun getAllMustTrnslLangsFreeQuestions(): Set<String>
}