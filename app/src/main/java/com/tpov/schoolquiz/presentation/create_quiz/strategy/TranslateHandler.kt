package com.tpov.schoolquiz.presentation.create_quiz.strategy

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
        ) = with(activity)  {
            TODO("Not yet implemented")
        }

    }