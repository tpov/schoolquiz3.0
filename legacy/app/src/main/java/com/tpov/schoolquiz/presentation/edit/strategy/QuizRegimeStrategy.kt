package com.tpov.schoolquiz.presentation.edit.strategy

import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import com.tpov.common.data.model.local.QuestionLocal
import com.tpov.common.presentation.model.PathStructure
import com.tpov.schoolquiz.presentation.edit.model.QuizUiModelState

interface QuizRegimeStrategy {
    fun setupUiState(): QuizUiModelState
    suspend fun loadData(pathStructure: PathStructure): QuizUiModelState
    fun fullscreen(isFullscreen: Boolean): QuizUiModelState
    suspend fun saveData(
        questionList: List<QuestionLocal>,
        bitmapList: Map<Pair<Int, Boolean>, BitmapDrawable>,
        structureList: List<Pair<String, BitmapDrawable>>,
        defaultImage: Drawable
    )
}
