package com.tpov.schoolquiz.presentation.create.strategy

import com.tpov.common.data.model.local.QuestionLocal
import com.tpov.common.data.model.local.StructureDataLocal
import com.tpov.common.presentation.model.PathStructure
import com.tpov.schoolquiz.presentation.create.model.QuizUiModelState

interface QuizRegimeStrategy {
    fun setupUiState(): QuizUiModelState
    suspend fun loadData(pathStructure: PathStructure): QuizUiModelState
    fun fullscreen(isFullscreen: Boolean): QuizUiModelState
    suspend fun saveData(questionList: List<QuestionLocal>, structureList: List<StructureDataLocal>)
}
