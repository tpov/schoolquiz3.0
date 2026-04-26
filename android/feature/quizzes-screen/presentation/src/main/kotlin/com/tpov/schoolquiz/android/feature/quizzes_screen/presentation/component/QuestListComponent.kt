package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component

import com.arkivanov.decompose.value.Value
import com.tpov.schoolquiz.android.core.designsystem.model.QuestDisplayItem
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.QuestListUiState

interface QuestListComponent {
    val uiState: Value<QuestListUiState>
    val titles: List<String>
    fun onQuestClick(quest: QuestDisplayItem)
    fun onShareClick(quest: QuestDisplayItem)
}
