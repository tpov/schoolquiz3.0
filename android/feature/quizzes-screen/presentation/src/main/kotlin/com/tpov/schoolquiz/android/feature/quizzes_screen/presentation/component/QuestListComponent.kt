package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component

import com.arkivanov.decompose.value.Value
import com.tpov.schoolquiz.android.core.designsystem.model.QuestDisplayItem
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuestListMode
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.QuestListUiState

interface QuestListComponent {
    val uiState: Value<QuestListUiState>
    val titles: List<String>
    val mode: QuestListMode
        get() = QuestListMode.Home

    fun onQuestClick(quest: QuestDisplayItem)

    fun onQuestDownloadClick(quest: QuestDisplayItem)

    fun onShareClick(quest: QuestDisplayItem)

    fun onRandomQuestClick() = Unit
}
