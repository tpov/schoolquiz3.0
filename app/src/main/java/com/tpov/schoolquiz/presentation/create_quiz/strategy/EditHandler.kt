package com.tpov.schoolquiz.presentation.create_quiz.strategy

import android.annotation.SuppressLint
import com.tpov.common.EventQuiz
import com.tpov.common.domain.model.StructureDataLocal
import com.tpov.schoolquiz.presentation.create_quiz.CreateQuizActivity
import com.tpov.schoolquiz.presentation.create_quiz.RegimeHandler

class EditHandler(private val activity: CreateQuizActivity) : RegimeHandler {
    override fun initViews(): Unit = with(activity) {
        CreateHandler(activity).initViews()

        setupQuestionSpinner()
        updateUiQuestion()
        setupUiQuiz()

        binding.bSave.text = "Update"
        viewModel.initQuestions()

    }

    override fun initData()  = with(activity) {
        CreateHandler(activity).initData()
    }

    override suspend fun saveData() = with(activity.viewModel){
        if (eventId == EventQuiz.QUIZ_BY_USER) {
            deleteOldStructureData()
            deleteOldQuestions()
            CreateHandler(activity).saveData()
        } else {
            getEditStructureData()
            questionsEntity.forEach {
                questionUseCase.insertQuestion(it)
            }
        }
    }
private fun getEditStructureData() {
    /*StructureEditData(
        null,
        activity.viewModel.pathStructure.idEvent,
        activity.viewModel.pathStructure.idCategory,
        activity.viewModel.pathStructure.idSubCategory,
        activity.viewModel.pathStructure.idSubsubCategory,
        activity.viewModel.pathStructure.idQuiz,


    )*/
}
    private suspend fun deleteOldStructureData() {
        val viewModel = activity.viewModel
        val quizToDelete = viewModel.quizEntity ?: return

        val rootStructure = viewModel.structureDataFlow.value?.find {
            it?.id == EventQuiz.QUIZ_BY_USER.id
        } ?: return

        val updatedStructure = deleteStructureRecursively(rootStructure, quizToDelete)
        viewModel.updateStructureData(updatedStructure!!, EventQuiz.QUIZ_BY_USER.id)
    }

    private fun deleteStructureRecursively(
        currentStructure: StructureDataLocal?,
        quizToDelete: StructureDataLocal?
    ): StructureDataLocal? {
        if (currentStructure?.nameItem == quizToDelete?.nameItem) {
            return null
        }

        val updatedChildren: MutableList<StructureDataLocal>? = currentStructure?.children?.mapNotNull { child ->
            deleteStructureRecursively(child, quizToDelete)
        }?.toMutableList()

        return currentStructure?.copy(children = updatedChildren)
    }

    @SuppressLint("SuspiciousIndentation")
    private suspend fun deleteOldQuestions() {
        val viewModel = activity.viewModel
            viewModel.questionUseCase.deleteQuestionByPath(viewModel.pathStructure)
    }
}
