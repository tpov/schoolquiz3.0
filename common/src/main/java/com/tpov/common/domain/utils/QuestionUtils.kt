package com.tpov.common.domain.utils

import com.tpov.common.data.model.local.QuestionEntity

typealias numLightQuestion = Int
typealias numHardQuestion = Int

object QuestionUtils {

    fun getNumsQuestion(questions: List<QuestionEntity>): Pair<numLightQuestion, numHardQuestion> {
        val lightCount = questions.count { !it.hardQuestion }
        val hardCount = questions.count { it.hardQuestion }
        return Pair(lightCount, hardCount)
    }
}
