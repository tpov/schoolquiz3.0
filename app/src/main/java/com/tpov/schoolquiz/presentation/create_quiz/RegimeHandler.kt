package com.tpov.schoolquiz.presentation.create_quiz

import com.tpov.common.data.model.local.QuestionEntity
import com.tpov.common.data.model.local.QuizEntity
import com.tpov.common.data.model.local.StructureCategoryDataEntity

interface RegimeHandler  {
    fun initViews()
    fun initData()
    suspend fun saveData(
        structureCategoryDataEntity: StructureCategoryDataEntity,
        quizIt: QuizEntity,
        questionsIt: ArrayList<QuestionEntity>)
}