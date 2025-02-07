package com.tpov.schoolquiz.presentation.create_quiz.strategy

import android.util.Log
import com.tpov.common.EventQuiz
import com.tpov.common.UNKNOWN_VALUE
import com.tpov.common.data.model.local.QuestionEntity
import com.tpov.common.domain.model.StructureDataLocal
import com.tpov.schoolquiz.presentation.create_quiz.CreateQuizActivity
import com.tpov.schoolquiz.presentation.create_quiz.RegimeHandler
import kotlinx.coroutines.flow.StateFlow

class CreateHandler(private val activity: CreateQuizActivity) : RegimeHandler {
    override fun initViews() {
        activity.viewModel.updateNewCounterAndShortList(true)

        Log.d(
            "rkfgujrdjkgjk",
            "CreateHandler questionsShortEntity : ${activity.viewModel.questionsShortEntity}"
        )
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

        mergeStructureData.children?.forEach {
            it?.let { activity.viewModel.updateStructureData(it, EventQuiz.QUIZ_BY_USER.id) }
        }

        activity.viewModel.questionsEntity.forEach {
            val question = updatePathQuestion(it)
            activity.viewModel.questionUseCase.insertQuestion(question)
        }
    }

    private fun updatePathQuestion(questionEntity: QuestionEntity): QuestionEntity {
        return questionEntity.copy(
            idEvent = activity.viewModel.pathStructure.idEvent,
            idCategory = activity.viewModel.pathStructure.idCategory,
            idSubCategory = activity.viewModel.pathStructure.idSubCategory,
            idSubsubCategory = activity.viewModel.pathStructure.idSubsubCategory,
            idQuiz = activity.viewModel.pathStructure.idQuiz
        )
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
        currentStructure.printFullStructure("jfgksdjefkse")

        if (currentStructure.children == null) {
            currentStructure.children = mutableListOf()
        }

        val eventRoot = currentStructure.children?.find {
            it?.id == eventId
        } ?: run {
            val newRoot = StructureDataLocal().create(id = eventId, "", 0, 0, "", "")
            currentStructure.children?.add(newRoot)
            newRoot
        }
        eventRoot.findOrCreateChild(categoryStructure).updatePathByStructureData()
            .findOrCreateChild(subCategoryStructure).updatePathByStructureData()
            .findOrCreateChild(subsubCategoryStructure).updatePathByStructureData()
            .findOrCreateChild(quizEntity).updatePathByStructureData()

        return currentStructure
    }

    private fun StructureDataLocal.updatePathByStructureData(): StructureDataLocal {
        activity.viewModel.pathStructure.apply {
            if (idCategory == UNKNOWN_VALUE) idCategory = id!!
            else if (idSubCategory == UNKNOWN_VALUE) idSubCategory = id!!
            else if (idSubsubCategory == UNKNOWN_VALUE) idSubsubCategory = id!!
            else if (idQuiz == UNKNOWN_VALUE) idQuiz = id!!
        }
        return this
    }

    private fun StructureDataLocal.findOrCreateChild(
        newChild: StructureDataLocal
    ): StructureDataLocal {
        if (children == null) {
            children = mutableListOf()
        }

        val existingChild = children?.find { it?.nameItem == newChild.nameItem }

        if (existingChild != null) {
            return existingChild
        }

        val newChildCopy = newChild.copy(
            id = children?.size?.plus(1) ?: 1
        )
        children?.add(newChildCopy)
        return newChildCopy
    }
}