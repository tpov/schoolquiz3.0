package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.fake

import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.tpov.schoolquiz.android.core.designsystem.model.QuestDisplayItem
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.component.QuestListComponent
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.config.BreadcrumbRoot
import com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate.QuestListUiState

class FakeQuestListComponent(
    initialState: QuestListUiState,
    override val breadcrumbs: List<BreadcrumbRoot> = emptyList(),
) : QuestListComponent {

    private val _uiState = MutableValue(initialState)
    override val uiState: Value<QuestListUiState> get() = _uiState

    var onQuestClickCalled: QuestDisplayItem? = null
    var onQuestDownloadClickCalled: QuestDisplayItem? = null
    var onShareClickCalled: QuestDisplayItem? = null

    fun setState(state: QuestListUiState) {
        _uiState.value = state
    }

    override fun onQuestClick(quest: QuestDisplayItem) {
        onQuestClickCalled = quest
    }

    override fun onQuestDownloadClick(quest: QuestDisplayItem) {
        onQuestDownloadClickCalled = quest
    }

    override fun onShareClick(quest: QuestDisplayItem) {
        onShareClickCalled = quest
    }
}
