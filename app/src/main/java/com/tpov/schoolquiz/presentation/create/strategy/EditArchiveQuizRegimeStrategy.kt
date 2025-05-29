package com.tpov.schoolquiz.presentation.create.strategy

import com.tpov.common.data.model.local.QuestionEntity
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

class EditArchiveQuizRegimeStrategy(structureUseCase: StructureUseCase, questionUseCase: QuestionUseCase) : QuizRegimeStrategy {
    override fun setupUiState() = CreateQuizUiModelState(
        quizNameUiState = TextUiState.Hidden,
        quizImageUiState = ImageUiState.Hidden,
        categorySpinnerUiState = SpinnerUiState.Hidden,
        subCategorySpinnerUiState = SpinnerUiState.Hidden,
        subsubCategorySpinnerUiState = SpinnerUiState.Hidden,
        llCreateNewCategory = ContainerUiState.Hidden,
        stroceTop = ContainerUiState.Hidden,

        questionImageUiState = ImageUiState.Visible(),
        fullscreenButtonUiState = CheckBoxUiState.Hidden,
        questionNumberSpinnerUiState = SpinnerUiState.Visible(),
        stroceBottom = ContainerUiState.Visible,
        bBeforeEditTranslate = TextUiState.Visible(),
        bAfterEditTranslate = TextUiState.Visible(),
        typeQuestionCheckBoxState = CheckBoxUiState.Visible(true),
        cancelButtonUiState = TextUiState.Visible(),
        addAnswerButtonUiState = TextUiState.Visible(),
        addTranslateButtonUiState = TextUiState.Visible(),
        addGapButtonUiState = TextUiState.Visible(),
        saveQuizButtonUiState = TextUiState.Visible(),
    )

    override suspend fun loadData(pathStructure: PathStructure): CreateQuizUiModelState {
        TODO("Not yet implemented")
    }


    override suspend fun saveData(questionList: List<QuestionEntity>, structureList: List<StructureDataLocal>) {
        TODO("Not yet implemented")
    }


}
