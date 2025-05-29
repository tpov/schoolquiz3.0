package com.tpov.schoolquiz.presentation.create.strategy

import com.tpov.common.data.model.local.QuestionEntity
import com.tpov.common.domain.model.StructureDataLocal
import com.tpov.common.presentation.model.PathStructure
import com.tpov.schoolquiz.presentation.create.model.CreateQuizUiModelState

interface QuizRegimeStrategy {
    fun setupUiState(): CreateQuizUiModelState
    suspend fun loadData(pathStructure: PathStructure): CreateQuizUiModelState
    suspend fun saveData(questionList: List<QuestionEntity>, structureList: List<StructureDataLocal>)
}
