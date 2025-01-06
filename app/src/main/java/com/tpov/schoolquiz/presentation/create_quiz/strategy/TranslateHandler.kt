package com.tpov.schoolquiz.presentation.create_quiz.strategy

import com.tpov.common.data.model.local.QuestionEntity
import com.tpov.common.data.model.local.QuizEntity
import com.tpov.common.data.model.local.StructureCategoryDataEntity
import com.tpov.schoolquiz.presentation.create_quiz.CreateQuizActivity
import com.tpov.schoolquiz.presentation.create_quiz.RegimeHandler

class TranslateHandler(private val activity: CreateQuizActivity) : RegimeHandler {
        override fun initViews() = with(activity)  {

            setupQuestionSpinner()
            updateUiQuestion()

            hideQuizViews()
            hideTopQuestionViews()
            binding.bSave.text = "Save"
        }

        override fun initData() {
            TODO("Not yet implemented")
        }

        override suspend fun saveData(
            structureCategoryDataEntity: StructureCategoryDataEntity,
            quizIt: QuizEntity,
            questionsIt: ArrayList<QuestionEntity>
        ) = with(activity)  {
            TODO("Not yet implemented")
        }

    }