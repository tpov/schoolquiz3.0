package com.tpov.common.domain.utils

import com.tpov.common.data.model.local.QuestionLocal
import com.tpov.common.presentation.model.PathStructure
import com.tpov.common.presentation.question.QuestionListResult
import com.tpov.common.presentation.utils.LanguageUtils

typealias numLightQuestion = Int
typealias numHardQuestion = Int

object QuestionUtils {

    fun List<QuestionLocal>.getNumsQuestion(mainLanguage: LanguageUtils? = null): Pair<numLightQuestion, numHardQuestion> {
        val lightCount = if (mainLanguage == null) count { !it.hardQuestion } else count { !it.hardQuestion && it.language == mainLanguage}
        val hardCount = if (mainLanguage == null) count { it.hardQuestion } else count { it.hardQuestion && it.language == mainLanguage }
        return Pair(lightCount, hardCount)
    }

    fun Pair<Int, Int>.sumPair() = this.first + this.second

    fun isDownloadQuestionForOptimization(currentPath: PathStructure, ratingGlobal: Int, size: Int): Boolean {
        return true
    }

    fun List<QuestionLocal>.filterByHardQuiz(hardQuiz: Boolean): List<QuestionLocal> {
        return this.filter { it.hardQuestion == hardQuiz }
    }

    fun List<QuestionLocal>.filterByLanguage(
        languageList: List<LanguageUtils>,
        numQuestions: Int
    ): List<QuestionLocal> {

        languageList.forEach { language ->
            val langList = this.filter { it.language == language }
            if (langList.size == numQuestions) return langList
        }
        return emptyList()
    }

    fun List<QuestionLocal>.getResultFilter(): QuestionListResult {
        return if (this.isEmpty()) QuestionListResult.EmptyTranslation
        else QuestionListResult.Success(this)
    }

    fun MutableList<QuestionLocal>?.updateQuestionsByNumber(
        questionNumber: Int,
        isHard: Boolean,
        update: (QuestionLocal) -> QuestionLocal
    ): List<QuestionLocal> {
        return this?.map { question ->
            if (question.numQuestion == questionNumber && question.hardQuestion == isHard) {
                update(question)
            } else question
        } ?: emptyList()
    }
}
