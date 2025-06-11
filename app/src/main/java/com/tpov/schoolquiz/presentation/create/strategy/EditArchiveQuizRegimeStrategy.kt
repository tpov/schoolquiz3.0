package com.tpov.schoolquiz.presentation.create.strategy

import com.tpov.common.data.model.local.QuestionLocal
import com.tpov.common.data.model.local.StructureDataLocal
import com.tpov.common.domain.usecase.QuestionUseCase
import com.tpov.common.domain.usecase.StructureUseCase
import com.tpov.common.presentation.model.PathStructure
import com.tpov.schoolquiz.presentation.create.model.CheckBoxUiState
import com.tpov.schoolquiz.presentation.create.model.ImageUiState
import com.tpov.schoolquiz.presentation.create.model.QuizUiModelState
import com.tpov.schoolquiz.presentation.create.model.SpinnerUiState
import com.tpov.schoolquiz.presentation.create.model.TextUiState
import com.tpov.schoolquiz.presentation.create.model.isUiState

class EditArchiveQuizRegimeStrategy(structureUseCase: StructureUseCase, questionUseCase: QuestionUseCase) : QuizRegimeStrategy {
    override fun setupUiState() = QuizUiModelState(
        quizNameUiState = TextUiState.Hidden,
        quizImageUiState = ImageUiState.Hidden,
        categorySpinnerUiState = SpinnerUiState.Hidden,
        subCategorySpinnerUiState = SpinnerUiState.Hidden,
        subsubCategorySpinnerUiState = SpinnerUiState.Hidden,
        llCreateNewCategory = isUiState.Hidden,
        stroceTop = isUiState.Hidden,

        questionImageUiState = ImageUiState.Visible(),
        fullscreenButtonUiState = CheckBoxUiState.Hidden,
        questionNumberSpinnerUiState = SpinnerUiState.Visible(),
        stroceBottom = isUiState.Visible,
        bBeforeEditTranslate = TextUiState.Visible(),
        bAfterEditTranslate = TextUiState.Visible(),
        typeQuestionCheckBoxState = CheckBoxUiState.Visible(true),
        cancelButtonUiState = TextUiState.Visible(),
        addAnswerButtonUiState = TextUiState.Visible(),
        addTranslateButtonUiState = TextUiState.Visible(),
        addGapButtonUiState = TextUiState.Visible(),
        saveQuizButtonUiState = TextUiState.Visible(),
    )

    override suspend fun loadData(pathStructure: PathStructure): QuizUiModelState {
        TODO("Not yet implemented")
    }

    override fun fullscreen(isFullscreen: Boolean): QuizUiModelState {
        TODO("Not yet implemented")
    }


    override suspend fun saveData(questionList: List<QuestionLocal>, structureList: List<StructureDataLocal>) {
        TODO("Not yet implemented")
    }


}
