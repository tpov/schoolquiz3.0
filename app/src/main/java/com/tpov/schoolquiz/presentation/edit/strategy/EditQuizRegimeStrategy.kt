package com.tpov.schoolquiz.presentation.edit.strategy

import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import com.tpov.common.data.model.local.QuestionLocal
import com.tpov.common.domain.usecase.QuestionUseCase
import com.tpov.common.domain.usecase.StructureUseCase
import com.tpov.common.presentation.model.PathStructure
import com.tpov.schoolquiz.presentation.edit.model.CheckBoxUiState
import com.tpov.schoolquiz.presentation.edit.model.ImageUiState
import com.tpov.schoolquiz.presentation.edit.model.IsUiState
import com.tpov.schoolquiz.presentation.edit.model.QuizUiModelState
import com.tpov.schoolquiz.presentation.edit.model.SpinnerUiState
import com.tpov.schoolquiz.presentation.edit.model.TextUiState


class EditQuizRegimeStrategy(
    val structureUseCase: StructureUseCase,
    val questionUseCase: QuestionUseCase
) : QuizRegimeStrategy {
    override fun setupUiState() = QuizUiModelState(
        quizNameUiState = TextUiState.Visible(),
        quizImageUiState = ImageUiState.Visible(),
        categorySpinnerUiState = SpinnerUiState.Visible(),
        subCategorySpinnerUiState = SpinnerUiState.Visible(),
        subsubCategorySpinnerUiState = SpinnerUiState.Visible(),
        llCreateNewCategory = IsUiState.Hidden,
        stroceTop = IsUiState.Visible,
        questionImageUiState = ImageUiState.Visible(),
        fullscreenButtonUiState = CheckBoxUiState.Visible(false),
        questionNumberSpinnerUiState = SpinnerUiState.Visible(),
        stroceBottom = IsUiState.Visible,
        bBeforeEditTranslate = TextUiState.Visible(),
        bAfterEditTranslate = TextUiState.Visible(),
        typeQuestionCheckBoxState = CheckBoxUiState.Visible(true),
        cancelButtonUiState = TextUiState.Visible(),
        addAnswerButtonUiState = TextUiState.Visible(),
        addTranslateButtonUiState = TextUiState.Visible(),
        saveQuizButtonUiState = TextUiState.Visible()
    )

    override suspend fun loadData(pathStructure: PathStructure): QuizUiModelState {
        TODO("Not yet implemented")
    }

    override fun fullscreen(isFullscreen: Boolean): QuizUiModelState {
        TODO("Not yet implemented")
    }

    override suspend fun saveData(
        questionList: List<QuestionLocal>,
        bitmapList: Map<Pair<Int, Boolean>, BitmapDrawable>,
        structureList: List<Pair<String, BitmapDrawable>>,
        defaultImage: Drawable
    ) {
        TODO("Not yet implemented")
    }



}
