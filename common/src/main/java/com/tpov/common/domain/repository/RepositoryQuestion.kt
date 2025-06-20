package com.tpov.common.domain.repository

import com.tpov.common.data.model.local.QuestionLocal
import com.tpov.common.presentation.model.PathStructure
import com.tpov.common.presentation.utils.LanguageUtils

interface RepositoryQuestion {
    suspend fun getAllMustTrnslLangsPaidQuestions(): Set<LanguageUtils>

    suspend fun fetchQuestion(
        pathStructure: PathStructure,
        language: List<LanguageUtils>,
    ): List<QuestionLocal>

    suspend fun getQuestionsByPath(path: PathStructure): List<QuestionLocal>
    suspend fun saveQuestion(questionEntity: QuestionLocal)
    suspend fun pushQuestion(
        questionLocal: QuestionLocal,
        isUpdate: Boolean = false
    )

    suspend fun updateQuestion(questionLocal: QuestionLocal)
    suspend fun deleteQuestionByPath(path: PathStructure)
    suspend fun deleteRemoteQuestionByIdQuiz(idQuiz: Int, typeId: Int)

    suspend fun remoteLangsQuestions(questionLocal: QuestionLocal): List<LanguageUtils>
    suspend fun getAllMustTrnslLangsFreeQuestions(): Set<LanguageUtils>
    suspend fun pushQuestionForTranslate(
        questionLocal: QuestionLocal,
        usePaidTranslation: Boolean,
        toLang: LanguageUtils,
    )
}
