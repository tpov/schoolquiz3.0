package com.tpov.schoolquiz.presentation.create_quiz.strategy

import com.tpov.common.EventQuiz
import com.tpov.common.data.model.local.QuestionEntity
import com.tpov.common.data.model.local.QuizEntity
import com.tpov.common.data.model.local.StructureCategoryDataEntity
import com.tpov.schoolquiz.presentation.create_quiz.RegimeHandler
import com.tpov.schoolquiz.presentation.create_quiz.RegimeHandlerImpl.initNewTranslateViews
import com.tpov.schoolquiz.presentation.create_quiz.RegimeHandlerImpl.setupQuestionSpinner
import com.tpov.schoolquiz.presentation.create_quiz.RegimeHandlerImpl.setupUiQuiz
import com.tpov.schoolquiz.presentation.create_quiz.RegimeHandlerImpl.updateUiQuestion
import com.tpov.schoolquiz.presentation.create_quiz.RegimeHandlerImpl.viewModel

class CreateHandler : RegimeHandler {
        override fun initViews() {
            viewModel.updateNewCounterAndShortList(true)
            setupQuestionSpinner()
            updateUiQuestion()
            setupUiQuiz()
            initNewTranslateViews()
        }

        override fun initData() {
            viewModel.initStructureData()
        }

        override suspend fun saveData(
            structureCategoryDataEntity: StructureCategoryDataEntity,
            quizIt: QuizEntity,
            questionsIt: ArrayList<QuestionEntity>) {
            val newIdQuiz = viewModel.getNewIdLocalQuiz()
            val updatedStructureCategoryData = structureCategoryDataEntity.copy(oldIdQuizId = newIdQuiz, newEventId = EventQuiz.QUIZ_BY_USER.id)
            val updatedQuizIt = quizIt.copy(id = newIdQuiz, event = EventQuiz.QUIZ_BY_USER.id)
            questionsIt.replaceAll { it.copy(idQuiz = newIdQuiz) }

            viewModel.structureUseCase.insertStructureCategoryData(updatedStructureCategoryData)
            viewModel.quizUseCase.insertQuiz(updatedQuizIt)
            questionsIt.forEach { viewModel.questionUseCase.insertQuestion(it) }
        }

    }