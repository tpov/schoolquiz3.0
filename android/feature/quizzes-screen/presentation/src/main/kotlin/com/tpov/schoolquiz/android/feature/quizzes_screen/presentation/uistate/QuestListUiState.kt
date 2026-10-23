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

    /**
     * Категория «Приватные квизы» (AD-29, история 8.4): строки — сохранённые приватные квизы
     * текущего владельца; тап открывает дверь прогона, а не иерархию публичного квеста.
     */
    data class PrivateQuests(
        val items: List<PrivateQuestItem>,
    ) : QuestListUiState
}

data class PrivateQuestItem(
    val questId: String,
    val title: String,
    val questionCount: Int,
) {
    init {
        require(questId.isNotBlank()) { "PrivateQuestItem.questId must not be blank" }
        require(questionCount >= 0) { "PrivateQuestItem.questionCount must be >= 0" }
    }
}
