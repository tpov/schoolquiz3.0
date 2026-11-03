package com.tpov.schoolquiz.presentation.edit.strategy

sealed class CreateQuizUiState {
    object Loading : CreateQuizUiState()
    object Create : CreateQuizUiState()
    data class Edit(val pathStructure: String?) : CreateQuizUiState()
    data class EditArchive(val pathStructure: String) : CreateQuizUiState()
    data class Translate(val pathStructure: String?) : CreateQuizUiState()
    data class Error(val message: String) : CreateQuizUiState()
}
