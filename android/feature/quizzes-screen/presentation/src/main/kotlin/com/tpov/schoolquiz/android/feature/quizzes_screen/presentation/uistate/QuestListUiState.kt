package com.tpov.schoolquiz.android.feature.quizzes_screen.presentation.uistate

import com.tpov.schoolquiz.android.core.designsystem.model.QuestDisplayItem
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId

sealed interface QuestListUiState {
    data object Loading : QuestListUiState

    data object Empty : QuestListUiState

    /**
     * @property shelfSync пометки о неподтверждённых переносах на полку — по квесту. Квеста нет в
     *   карте, когда очередь о нём ничего не знает.
     */
    data class Loaded(
        val quests: List<QuestDisplayItem>,
        val shelfSync: Map<QuestId, QuestShelfSyncMark> = emptyMap(),
    ) : QuestListUiState
}
