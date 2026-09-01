package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component

import com.arkivanov.decompose.value.Value
import com.tpov.schoolquiz.android.core.designsystem.model.QuestDisplayItem
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.BreadcrumbRoot
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.QuestListMode
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.QuestListUiState

interface QuestListComponent {
    val uiState: Value<QuestListUiState>
    val breadcrumbs: List<BreadcrumbRoot>
    val mode: QuestListMode
        get() = QuestListMode.Home
    val selectionTargetShelf: String?
        get() = null

    fun onQuestClick(quest: QuestDisplayItem)

    fun onQuestDownloadClick(quest: QuestDisplayItem)

    fun onShareClick(quest: QuestDisplayItem)

    fun onRandomQuestClick() = Unit

    fun onSetShelfClick(
        quest: QuestDisplayItem,
        targetShelf: String,
    ) = Unit

    /** Takes the quest off every shelf. Developer-gated, same as the shelf moves. */
    fun onRetireClick(quest: QuestDisplayItem) = Unit
}
