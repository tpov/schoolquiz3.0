package com.tpov.schoolquiz.presentation.create_quiz.strategy

import com.tpov.common.data.model.local.QuestionEntity
import com.tpov.common.data.model.local.QuizEntity
import com.tpov.common.data.model.local.StructureCategoryDataEntity
import com.tpov.schoolquiz.presentation.create_quiz.RegimeHandler
import com.tpov.schoolquiz.presentation.create_quiz.RegimeHandlerImpl.binding
import com.tpov.schoolquiz.presentation.create_quiz.RegimeHandlerImpl.hideQuizViews
import com.tpov.schoolquiz.presentation.create_quiz.RegimeHandlerImpl.hideTopQuestionViews
import com.tpov.schoolquiz.presentation.create_quiz.RegimeHandlerImpl.setupQuestionSpinner
import com.tpov.schoolquiz.presentation.create_quiz.RegimeHandlerImpl.updateUiQuestion

class TranslateHandler : RegimeHandler {
        override fun initViews() {

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
        ) {
            TODO("Not yet implemented")
        }

    }