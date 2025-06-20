package com.tpov.common.domain.usecase

import android.util.Log
import com.tpov.common.data.model.local.QuestionLocal
import com.tpov.common.domain.repository.RepositoryQuestion
import com.tpov.common.presentation.model.PathStructure
import com.tpov.common.presentation.utils.LanguageUtils
import javax.inject.Inject

class QuestionUseCase @Inject constructor(private val repositoryQuestion: RepositoryQuestion) {

    suspend fun getQuestionByPath(pathStructure: PathStructure): ArrayList<QuestionLocal> {
        val questionList = repositoryQuestion.getQuestionsByPath(pathStructure)
        return ArrayList(questionList)
    }

    suspend fun insertQuestion(questionLocal: QuestionLocal) {
        Log.d("insertQuestion", "$questionLocal")
        repositoryQuestion.saveQuestion(questionLocal)
    }

    suspend fun pushQuestion(questionLocal: QuestionLocal) {
        repositoryQuestion.pushQuestion(
            questionLocal
        )
    }

    suspend fun fetchQuestion(pathStructure: PathStructure, languages: List<LanguageUtils>) =
        repositoryQuestion.fetchQuestion(pathStructure, languages)

    suspend fun pushQuestionForTranslate(
        question: QuestionLocal,
        localLangsQuestions: Set<LanguageUtils>,
    ) {
        val remoteLangsQuestions = repositoryQuestion.remoteLangsQuestions(question).toSet()
        val allLangsQuestions = remoteLangsQuestions + localLangsQuestions

        val allMustPaidLangsQuestions = repositoryQuestion.getAllMustTrnslLangsPaidQuestions()
        val allMustFreeLangsQuestions = repositoryQuestion.getAllMustTrnslLangsFreeQuestions()

        val mustLangsPaidQuestions = allMustPaidLangsQuestions.subtract(allLangsQuestions)
        val mustLangsFreeQuestions = allMustFreeLangsQuestions.subtract(allLangsQuestions)

        mustLangsFreeQuestions.forEach { pushTranslation(question, false, it) }
        mustLangsPaidQuestions.forEach { pushTranslation(question, true, it) }
    }

    private suspend fun pushTranslation(
        question: QuestionLocal,
        isPaid: Boolean,
        toLang: LanguageUtils,
    ) {
        repositoryQuestion.pushQuestionForTranslate(
            question, isPaid, toLang
        )
    }

    suspend fun updateQuestion(questionLocal: QuestionLocal) {
        repositoryQuestion.updateQuestion(questionLocal)
    }

    suspend fun deleteQuestionByPath(pathStructure: PathStructure) {
        repositoryQuestion.deleteQuestionByPath(pathStructure)
    }

    suspend fun deleteRemoteQuestionByIdQuiz(
        idQuiz: Int,
        typeId: Int
    ) {
        repositoryQuestion.deleteRemoteQuestionByIdQuiz(idQuiz, typeId)
    }

}
