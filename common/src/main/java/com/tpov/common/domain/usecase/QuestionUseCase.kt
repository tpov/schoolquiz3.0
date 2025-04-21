package com.tpov.common.domain.usecase

import android.util.Log
import com.tpov.common.data.model.local.QuestionEntity
import com.tpov.common.domain.repository.RepositoryQuestion
import com.tpov.common.presentation.model.PathStructure
import javax.inject.Inject

class QuestionUseCase @Inject constructor(private val repositoryQuestion: RepositoryQuestion) {

    suspend fun getQuestionByPath(pathStructure: PathStructure): ArrayList<QuestionEntity> {
        val questionList = repositoryQuestion.getQuestionsByPath(pathStructure)
        return ArrayList(questionList)
    }

    suspend fun insertQuestion(questionEntity: QuestionEntity) {
        Log.d("insertQuestion", "$questionEntity")
        repositoryQuestion.saveQuestion(questionEntity)
    }

    suspend fun pushQuestion(questionEntity: QuestionEntity) {
        repositoryQuestion.pushQuestion(
            questionEntity
        )
    }

    suspend fun pushQuestionForTranslate(
        question: QuestionEntity,
        localLangsQuestions: Set<String>,
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
        question: QuestionEntity,
        isPaid: Boolean,
        toLang: String,
    ) {
        repositoryQuestion.pushQuestionForTranslate(
            question, isPaid, toLang
        )
    }

    suspend fun updateQuestion(questionEntity: QuestionEntity) {
        repositoryQuestion.updateQuestion(questionEntity)
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