package com.tpov.schoolquiz.presentation.create_quiz.strategy

import com.tpov.common.data.model.local.QuestionEntity
import com.tpov.common.data.model.local.QuizEntity
import com.tpov.common.data.model.local.StructureCategoryDataEntity
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
            structureCategoryDataEntity: StructureCategoryDataEntity,
            quizIt: QuizEntity,
            questionsIt: ArrayList<QuestionEntity>
        )  = with(activity) {
            val newIdQuiz = viewModel.getNewIdLocalQuiz()
            val structureCategoryDataEntity = structureCategoryDataEntity.copy(newEventId = 3, oldIdQuizId = newIdQuiz)
            viewModel.structureUseCase.insertStructureCategoryData(structureCategoryDataEntity)
            viewModel.quizUseCase.updateQuiz(quizIt)
            viewModel.questionUseCase.deleteQuestionByIdQuiz(quizIt.id!!)
            questionsIt.forEach { viewModel.questionUseCase.insertQuestion(it) }
        }


    }