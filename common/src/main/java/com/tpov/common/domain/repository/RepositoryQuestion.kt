package com.tpov.common.domain.repository

import com.tpov.common.data.model.local.QuestionEntity
import com.tpov.common.presentation.model.PathStructure

interface RepositoryQuestion {
    suspend fun getAllMustTrnslLangsPaidQuestions(): Set<String>

    suspend fun fetchQuestion(
        pathStructure: PathStructure,
        language: String,
    ): List<QuestionEntity>

    suspend fun getQuestionsByPath(path: PathStructure): List<QuestionEntity>
    suspend fun saveQuestion(questionEntity: QuestionEntity)
    suspend fun pushQuestion(
        questionEntity: QuestionEntity,
        isUpdate: Boolean = false
    )

    suspend fun updateQuestion(questionEntity: QuestionEntity)
    suspend fun deleteQuestionByIdQuiz(idQuiz: Int)
    suspend fun deleteRemoteQuestionByIdQuiz(idQuiz: Int, typeId: Int)

    suspend fun remoteLangsQuestions(questionEntity: QuestionEntity): List<String>
    suspend fun getAllMustTrnslLangsFreeQuestions(): Set<String>
    suspend fun pushQuestionForTranslate(
        question: QuestionEntity,
        usePaidTranslation: Boolean,
        toLang: String,
    )
}