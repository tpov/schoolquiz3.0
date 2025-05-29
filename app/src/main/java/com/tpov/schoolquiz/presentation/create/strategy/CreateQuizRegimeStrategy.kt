package com.tpov.schoolquiz.presentation.create.strategy

import com.tpov.common.data.model.local.QuestionEntity
import com.tpov.common.domain.model.EventQuiz
import com.tpov.common.domain.model.StructureDataLocal
import com.tpov.common.domain.usecase.QuestionUseCase
import com.tpov.common.domain.usecase.StructureUseCase
import com.tpov.common.presentation.model.PathStructure
import com.tpov.schoolquiz.presentation.create.model.CheckBoxUiState
import com.tpov.schoolquiz.presentation.create.model.ContainerUiState
import com.tpov.schoolquiz.presentation.create.model.CreateQuizUiModelState
import com.tpov.schoolquiz.presentation.create.model.ImageUiState
import com.tpov.schoolquiz.presentation.create.model.SpinnerUiState
import com.tpov.schoolquiz.presentation.create.model.TextUiState


class CreateQuizRegimeStrategy(
    val structureUseCase: StructureUseCase,
    val questionUseCase: QuestionUseCase
) : QuizRegimeStrategy {

    override fun setupUiState() = CreateQuizUiModelState(
        quizNameUiState = TextUiState.Visible(),
        quizImageUiState = ImageUiState.Visible(),
        categorySpinnerUiState = SpinnerUiState.Visible(),
        subCategorySpinnerUiState = SpinnerUiState.Visible(),
        subsubCategorySpinnerUiState = SpinnerUiState.Visible(),

        llCreateNewCategory = ContainerUiState.Hidden,
        stroceTop = ContainerUiState.Visible,
        questionImageUiState = ImageUiState.Visible(),
        fullscreenButtonUiState = CheckBoxUiState.Visible(false),
        questionNumberSpinnerUiState = SpinnerUiState.Visible(),
        stroceBottom = ContainerUiState.Visible,
        bBeforeEditTranslate = TextUiState.Hidden,
        bAfterEditTranslate = TextUiState.Hidden,
        typeQuestionCheckBoxState = CheckBoxUiState.Visible(true),
        cancelButtonUiState = TextUiState.Visible(),
        addAnswerButtonUiState = TextUiState.Visible(),
        addTranslateButtonUiState = TextUiState.Visible(),
        addGapButtonUiState = TextUiState.Visible(),
        saveQuizButtonUiState = TextUiState.Visible(),
    )

    override suspend fun loadData(pathStructure: PathStructure): CreateQuizUiModelState {
        val homeQuizCategoryStructure =
            structureUseCase.getStructureEventData(EventQuiz.fromInput(EventQuiz.QUIZ_HOME)!!)
        val userQuizCategoryStructure =
            structureUseCase.getStructureEventData(EventQuiz.fromInput(EventQuiz.QUIZ_BY_USER)!!)

        val categoryList: MutableList<String> = mutableListOf()
        val subcategoryList: MutableList<String> = mutableListOf()
        val subsubCategoryList: MutableList<String> = mutableListOf()

        homeQuizCategoryStructure?.forEach { structure -> structure.children?.mapTo(categoryList) { it.nameItem } }
        homeQuizCategoryStructure?.getOrNull(0)?.children?.forEach { structure ->
            structure.children?.mapTo(subcategoryList) { it.nameItem }
        }
        userQuizCategoryStructure?.forEach { structure -> structure.children?.mapTo(subcategoryList) { it.nameItem } }
        homeQuizCategoryStructure?.getOrNull(0)?.children?.getOrNull(0)?.children?.forEach { structure ->
            structure.children?.mapTo(subsubCategoryList) { it.nameItem }
        }

        return CreateQuizUiModelState(
            categorySpinnerUiState = SpinnerUiState.Visible(categoryList, 0),
            subCategorySpinnerUiState = SpinnerUiState.Visible(subcategoryList, 0),
            subsubCategorySpinnerUiState = SpinnerUiState.Visible(subsubCategoryList, 0),
        )
    }

    override suspend fun saveData(questionList: List<QuestionEntity>, structureList: List<StructureDataLocal>) {

        var structureCategoryHomeList = structureUseCase.getStructureEventData(EventQuiz.QUIZ_BY_USER)?.toMutableList() ?: mutableListOf()

        var structureCategoryHome = structureCategoryHomeList.find { it.nameItem == structureList[0].nameItem }
        if (structureCategoryHome == null) {
            structureCategoryHome = StructureDataLocal(nameItem = structureList[0].nameItem, children = mutableListOf())
            structureCategoryHomeList.add(structureCategoryHome)
        }

        var structureSubCategoryHome = structureCategoryHome.children?.find { it.nameItem == structureList[1].nameItem }
        if (structureSubCategoryHome == null) {
            structureSubCategoryHome = StructureDataLocal(nameItem = structureList[1].nameItem, children = mutableListOf())
            structureCategoryHome.children?.add(structureSubCategoryHome)
        }
        var structureSubsubCategoryHome = structureSubCategoryHome.children?.find { it.nameItem == structureList[2].nameItem }
        if (structureSubsubCategoryHome == null) {
            structureSubsubCategoryHome = StructureDataLocal(nameItem = structureList[2].nameItem, children = mutableListOf())
            structureSubCategoryHome.children?.add(structureSubsubCategoryHome)
        }

        var structureQuizHome = structureSubsubCategoryHome.children?.find { it.nameItem == structureList[3].nameItem }
        if (structureQuizHome == null) {
            structureQuizHome = StructureDataLocal(nameItem = structureList[3].nameItem)
            structureSubsubCategoryHome.children?.add(structureQuizHome)
        }

        structureUseCase.updateStructureDataList(structureCategoryHomeList, EventQuiz.QUIZ_BY_USER)
        questionList.forEach { questionUseCase.insertQuestion(it) }
    }

}
