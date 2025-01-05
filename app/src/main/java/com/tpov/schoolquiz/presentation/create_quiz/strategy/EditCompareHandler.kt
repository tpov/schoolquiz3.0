package com.tpov.schoolquiz.presentation.create_quiz.strategy

import com.tpov.common.data.model.local.QuestionEntity
import com.tpov.common.data.model.local.QuizEntity
import com.tpov.common.data.model.local.StructureCategoryDataEntity
import com.tpov.schoolquiz.presentation.create_quiz.RegimeHandler
import com.tpov.schoolquiz.presentation.create_quiz.RegimeHandlerImpl.binding
import com.tpov.schoolquiz.presentation.create_quiz.RegimeHandlerImpl.setupQuestionSpinner
import com.tpov.schoolquiz.presentation.create_quiz.RegimeHandlerImpl.setupUiQuiz
import com.tpov.schoolquiz.presentation.create_quiz.RegimeHandlerImpl.updateUiQuestion
import com.tpov.schoolquiz.presentation.create_quiz.RegimeHandlerImpl.viewModel

class EditCompareHandler : RegimeHandler {
        override fun initViews() {

            setupQuestionSpinner()
            updateUiQuestion()
            setupUiQuiz()

            binding.bSave.text = "Update in archive"
        }

        override fun initData() {
            TODO("Not yet implemented")
        }

        override suspend fun saveData(
            structureCategoryDataEntity: StructureCategoryDataEntity,
            quizIt: QuizEntity,
            questionsIt: ArrayList<QuestionEntity>
        ) {
            val newIdQuiz = viewModel.getNewIdLocalQuiz()
            val structureCategoryDataEntity = structureCategoryDataEntity.copy(newEventId = 3, oldIdQuizId = newIdQuiz)
            viewModel.structureUseCase.insertStructureCategoryData(structureCategoryDataEntity)
            viewModel.quizUseCase.updateQuiz(quizIt)
            viewModel.questionUseCase.deleteQuestionByIdQuiz(quizIt.id!!)
            questionsIt.forEach { viewModel.questionUseCase.insertQuestion(it) }
        }


    }