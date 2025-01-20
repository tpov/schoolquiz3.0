package com.tpov.schoolquiz.presentation.create_quiz.strategy

import com.tpov.schoolquiz.presentation.create_quiz.CreateQuizActivity
import com.tpov.schoolquiz.presentation.create_quiz.RegimeHandler

class EditCompareHandler(private val activity: CreateQuizActivity) : RegimeHandler {
        override fun initViews() = with(activity) {

            setupQuestionSpinner()
            updateUiQuestion()
            setupUiQuiz()

            binding.bSave.text = "Update in archive"
        }

        override fun initData() = with(activity)  {
            TODO("Not yet implemented")
        }

        override suspend fun saveData(
        )  = with(activity) {
        }
    }