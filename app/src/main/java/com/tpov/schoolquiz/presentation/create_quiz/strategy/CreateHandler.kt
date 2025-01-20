package com.tpov.schoolquiz.presentation.create_quiz.strategy

import com.tpov.common.EventQuiz
import com.tpov.common.domain.model.StructureDataLocal
import com.tpov.schoolquiz.presentation.create_quiz.CreateQuizActivity
import com.tpov.schoolquiz.presentation.create_quiz.RegimeHandler
import kotlinx.coroutines.flow.StateFlow

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

    private fun updateNumQuestions(mergeStructureData: StructureDataLocal) {
        TODO("Not yet implemented")
    }

    override suspend fun saveData() {
        val mergeStructureData = mergeNewStructureData(
            activity.viewModel.categoryStructure,
            activity.viewModel.subCategoryStructure,
            activity.viewModel.subsubCategoryStructure,
            activity.viewModel.quizEntity!!,
            activity.viewModel.structureDataFlow
        )

        updateNumQuestions(mergeStructureData)
        activity.viewModel.updateStructureData(mergeStructureData, EventQuiz.QUIZ_BY_USER.id)
        activity.viewModel.questionsEntity.forEach {
            activity.viewModel.questionUseCase.insertQuestion(
                it
            )
        }
    }

    private fun mergeNewStructureData(
        categoryStructure: StructureDataLocal,
        subCategoryStructure: StructureDataLocal,
        subsubCategoryStructure: StructureDataLocal,
        quizEntity: StructureDataLocal,
        structureDataFlow: StateFlow<StructureDataLocal?>
    ): StructureDataLocal {
        val eventId = EventQuiz.QUIZ_BY_USER.id

        val structureCategoryData = structureDataFlow.value?.childes?.find { it.id == eventId }

        val updateCategoryStructureData =
            updateStructureDataByFindName(structureCategoryData, categoryStructure)
        val updateSubCategoryStructureData =
            updateStructureDataByFindName(updateCategoryStructureData, subCategoryStructure)
        val updateSubsubCategoryStructureData =
            updateStructureDataByFindName(updateSubCategoryStructureData, subsubCategoryStructure)
        val updateQuizEntityData =
            updateStructureDataByFindName(updateSubsubCategoryStructureData, quizEntity)

        return updateQuizEntityData ?: structureCategoryData ?: categoryStructure
    }

    private fun updateStructureDataByFindName(
        originalCategoryData: StructureDataLocal?,
        newCategoryData: StructureDataLocal
    ): StructureDataLocal? {
        if (originalCategoryData == null) {
            return newCategoryData.copy(
                childes = mutableListOf(),
                id = 1
            )
        }

        if (originalCategoryData.childes == null) {
            originalCategoryData.childes = mutableListOf()
        }

        val foundCategoryData =
            originalCategoryData.childes?.find { it.nameItem == newCategoryData.nameItem }

        return if (foundCategoryData != null) {
            foundCategoryData
        } else {
            val newId = (originalCategoryData.childes?.size ?: 0) + 1
            val newStructureCategory = newCategoryData.copy(
                id = newId,
                childes = mutableListOf()
            )
            originalCategoryData.childes?.add(newStructureCategory)
            newStructureCategory
        }
    }
}