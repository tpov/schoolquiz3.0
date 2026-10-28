package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestProposalBox
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestProposalPage

/**
 * Читающая проекция «Предложений» (история 4.25, UX-22): права решает серверная выборка.
 *
 * Решение автора уходит существующим [DecideQuestProposalUseCase] — тот же приёмник 4.21,
 * здесь его не дублируют.
 */
interface QuestProposalsRepository {
    suspend fun fetch(
        box: QuestProposalBox,
        limit: Int,
        cursor: String?,
    ): Result<QuestProposalPage>
}
