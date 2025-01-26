package com.tpov.schoolquiz.presentation.create_quiz.strategy

import android.util.Log
import com.tpov.common.EventQuiz
import com.tpov.common.domain.model.StructureDataLocal
import com.tpov.schoolquiz.presentation.create_quiz.CreateQuizActivity
import com.tpov.schoolquiz.presentation.create_quiz.RegimeHandler
import kotlinx.coroutines.flow.StateFlow

class CreateHandler(private val activity: CreateQuizActivity) : RegimeHandler {
    override fun initViews() {
        activity.viewModel.updateNewCounterAndShortList(true)

        Log.d("rkfgujrdjkgjk", "CreateHandler questionsShortEntity : ${activity.viewModel.questionsShortEntity}")
        activity.setupQuestionSpinner()
        activity.updateUiQuestion()
        activity.setupUiQuiz()
        activity.initNewTranslateViews()

    }

    override fun initData() {
        activity.viewModel.initStructureData()
    }

    override suspend fun saveData() {

        val mergeStructureData = mergeNewStructureData(
            activity.viewModel.categoryStructure,
            activity.viewModel.subCategoryStructure,
            activity.viewModel.subsubCategoryStructure,
            activity.viewModel.quizEntity!!,
            activity.viewModel.structureDataFlow
        )
        mergeStructureData.childes?.forEach {

            Log.d("StructureTree", "it: $it")
it?.let { activity.viewModel.updateStructureData(it, EventQuiz.QUIZ_BY_USER.id) }

        }
        activity.viewModel.questionsEntity.forEach {
            activity.viewModel.questionUseCase.insertQuestion(it)
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
        val currentStructure = structureDataFlow.value ?: StructureDataLocal()
        currentStructure.printFullStructure()

        if (currentStructure.childes == null) {
            currentStructure.childes = mutableListOf()
        }

        val eventRoot = currentStructure.childes?.find {
            it?.id == eventId } ?: run {
            val newRoot = StructureDataLocal().create(id = eventId, "",0,0,"", "")
            currentStructure.childes?.add(newRoot)
            newRoot
        }
        eventRoot.findOrCreateChild(categoryStructure)
            .findOrCreateChild(subCategoryStructure)
            .findOrCreateChild(subsubCategoryStructure)
            .findOrCreateChild(quizEntity)

        currentStructure.printFullStructure()
        return currentStructure
    }

    private fun StructureDataLocal.findOrCreateChild(
        newChild: StructureDataLocal
    ): StructureDataLocal {
        if (childes == null) {
            childes = mutableListOf()
        }
        Log.d("findOrCreateChild", "Current structure: $this")
        Log.d("findOrCreateChild", "New child to add: $newChild")

        newChild.printFullStructure()
        this.printFullStructure()

        val existingChild = childes?.find { it?.nameItem == newChild.nameItem }


        if (existingChild != null) {
            Log.d("findOrCreateChild", "Existing child found: $existingChild")
            return existingChild
        }

        val newChildCopy = newChild.copy(
            id = childes?.size?.plus(1) ?: 1
        )
        childes?.add(newChildCopy)
        this.printFullStructure()
        newChildCopy.printFullStructure()
        Log.d("findOrCreateChild", "Added new child: $newChildCopy")
        return newChildCopy
    }
}