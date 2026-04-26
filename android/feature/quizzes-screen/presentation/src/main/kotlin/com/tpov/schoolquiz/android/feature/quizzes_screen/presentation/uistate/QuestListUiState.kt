package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate

import com.tpov.schoolquiz.android.core.designsystem.model.QuestDisplayItem

sealed interface QuestListUiState {
    data object Loading : QuestListUiState
    data object Empty : QuestListUiState
    data class Loaded(val quests: List<QuestDisplayItem>) : QuestListUiState
}
