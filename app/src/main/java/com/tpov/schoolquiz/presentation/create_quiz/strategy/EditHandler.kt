package com.tpov.schoolquiz.presentation.create_quiz.strategy

import com.tpov.common.data.model.local.QuestionEntity
import com.tpov.common.data.model.local.QuizEntity
import com.tpov.common.data.model.local.StructureCategoryDataEntity
import com.tpov.schoolquiz.presentation.create_quiz.CreateQuizActivity
import com.tpov.schoolquiz.presentation.create_quiz.RegimeHandler

class EditHandler(private val activity: CreateQuizActivity) : RegimeHandler {
    override fun initViews()  = with(activity) {
        CreateHandler(activity).initViews()

        setupQuestionSpinner()
        updateUiQuestion()
        setupUiQuiz()

        binding.bSave.text = "Update"

    }

    override fun initData()  = with(activity) {
        CreateHandler(activity).initData()
    }

    override suspend fun saveData(
        structureCategoryDataEntity: StructureCategoryDataEntity,
        quizIt: QuizEntity,
        questionsIt: ArrayList<QuestionEntity>
    ) = with(activity)  {
        viewModel.structureUseCase.insertStructureCategoryData(structureCategoryDataEntity)
        viewModel.quizUseCase.updateQuiz(quizIt)
        viewModel.questionUseCase.deleteQuestionByIdQuiz(quizIt.id!!)
        questionsIt.forEach { viewModel.questionUseCase.insertQuestion(it) }
    }
}
