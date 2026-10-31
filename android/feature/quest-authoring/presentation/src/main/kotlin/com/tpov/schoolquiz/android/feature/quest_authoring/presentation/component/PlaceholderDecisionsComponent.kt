package com.tpov.schoolquiz.android.feature.quest_authoring.presentation.component

import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.DecisionsUiState
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewDecisionFilter
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewDecisionMarkValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Заглушка «Решений» (история 4.17): вечная загрузка вместо тишины — как у прочих заглушек
 * разделов шторки. Настоящая проводка поднимается в app-shell модуле.
 */
class PlaceholderDecisionsComponent : DecisionsComponent {
    private val _state = MutableStateFlow(DecisionsUiState())
    override val state: StateFlow<DecisionsUiState> = _state

    override fun onFilterSelected(filter: ReviewDecisionFilter) = Unit

    override fun onLoadMoreClick() = Unit

    override fun onEntrySelected(
        verdictId: String,
        questionId: String,
    ) = Unit

    override fun onBackToListClick() = Unit

    override fun onMarkSelected(mark: ReviewDecisionMarkValue) = Unit

    override fun onCommentChanged(value: String) = Unit
}
