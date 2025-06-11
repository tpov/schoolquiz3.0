package com.tpov.common.domain.utils

import com.tpov.common.data.model.entity.QuestionEntity
import com.tpov.common.data.model.local.QuestionLocal
import com.tpov.common.presentation.model.PathStructure
import com.tpov.common.presentation.question.QuestionListResult
import com.tpov.common.presentation.utils.LanguageUtils

typealias numLightQuestion = Int
typealias numHardQuestion = Int

object QuestionUtils {

    fun getNumsQuestion(questions: List<QuestionEntity>): Pair<numLightQuestion, numHardQuestion> {
        val lightCount = questions.count { !it.hardQuestion }
        val hardCount = questions.count { it.hardQuestion }
        return Pair(lightCount, hardCount)
    }

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
}
