package com.tpov.schoolquiz.presentation.create_quiz.strategy

import com.tpov.common.EventQuiz
import com.tpov.common.data.model.local.QuestionEntity
import com.tpov.common.data.model.local.QuizEntity
import com.tpov.common.data.model.local.StructureCategoryDataEntity
import com.tpov.schoolquiz.presentation.create_quiz.CreateQuizActivity
import com.tpov.schoolquiz.presentation.create_quiz.RegimeHandler

class CreateHandler(private val activity: CreateQuizActivity) : RegimeHandler {
        override fun initViews() {
            activity.viewModel.updateNewCounterAndShortList(true)
            activity.setupQuestionSpinner()
            activity.updateUiQuestion()
            activity.setupUiQuiz()
            activity.initNewTranslateViews()
        }

        override fun initData() {
            activity.viewModel.initStructureData()
        }

        override suspend fun saveData(
            structureCategoryDataEntity: StructureCategoryDataEntity,
            quizIt: QuizEntity,
            questionsIt: ArrayList<QuestionEntity>) {
            val newIdQuiz = activity.viewModel.getNewIdLocalQuiz()
            val updatedStructureCategoryData = structureCategoryDataEntity.copy(oldIdQuizId = newIdQuiz, newEventId = EventQuiz.QUIZ_BY_USER.id)
            val updatedQuizIt = quizIt.copy(id = newIdQuiz, event = EventQuiz.QUIZ_BY_USER.id)
            questionsIt.replaceAll { it.copy(idQuiz = newIdQuiz) }

            activity.viewModel.createNewCategory(EventQuiz.QUIZ_BY_USER.id, activity.viewModel.category,activity.viewModel.subCategory,activity.viewModel.subsubCategory, )
            activity.viewModel.quizUseCase.insertQuiz(updatedQuizIt)
            questionsIt.forEach { activity.viewModel.questionUseCase.insertQuestion(it) }
        }

    }