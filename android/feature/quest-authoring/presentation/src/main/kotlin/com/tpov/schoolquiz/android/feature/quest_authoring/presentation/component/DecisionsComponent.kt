package com.tpov.schoolquiz.android.feature.quest_authoring.presentation.component

import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.DecisionsSurfaceUi
import com.tpov.schoolquiz.android.feature.quest_authoring.presentation.uistate.DecisionsUiState
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestProposalBox
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewDecisionFilter
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewDecisionMarkValue
import kotlinx.coroutines.flow.StateFlow

/**
 * Вкладка «Решения» (история 4.17, UX-22): аудит по-вопросных вердиктов модели для админов.
 *
 * Право решает сервер: не-админ получает отказ и экран показывает только «Предложения» —
 * компонент не пытается спрятать данные сам, он честно отображает отказ.
 */
interface DecisionsComponent {
    val state: StateFlow<DecisionsUiState>

    fun onFilterSelected(filter: ReviewDecisionFilter)

    fun onLoadMoreClick()

    fun onEntrySelected(
        verdictId: String,
        questionId: String,
    )

    fun onBackToListClick()

    fun onMarkSelected(mark: ReviewDecisionMarkValue)

    fun onCommentChanged(value: String)

    /** Поверхность вкладки: вердикты (админ) или «Предложения» (история 4.25). */
    fun onSurfaceSelected(surface: DecisionsSurfaceUi)

    /** Полка «Предложений»: входящие (владельцу) или исходящие (предложившему). */
    fun onProposalsBoxSelected(box: QuestProposalBox)

    fun onLoadMoreProposalsClick()

    fun onProposalSelected(proposalId: String)

    fun onBackToProposalsListClick()

    /** Решение автора: approve=true — одобрить, false — отклонить (приёмник 4.21). */
    fun onProposalDecision(approve: Boolean)

    fun onProposalCommentChanged(value: String)
}
