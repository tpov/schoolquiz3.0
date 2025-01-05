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

class EditHandler : RegimeHandler {
    override fun initViews() {
        CreateHandler().initViews()

        setupQuestionSpinner()
        updateUiQuestion()
        setupUiQuiz()

        binding.bSave.text = "Update"

    }

    override fun initData() {
        CreateHandler().initData()
    }

    override suspend fun saveData(
        structureCategoryDataEntity: StructureCategoryDataEntity,
        quizIt: QuizEntity,
        questionsIt: ArrayList<QuestionEntity>
    ) {
        viewModel.structureUseCase.insertStructureCategoryData(structureCategoryDataEntity)
        viewModel.quizUseCase.updateQuiz(quizIt)
        viewModel.questionUseCase.deleteQuestionByIdQuiz(quizIt.id!!)
        questionsIt.forEach { viewModel.questionUseCase.insertQuestion(it) }
    }
}
