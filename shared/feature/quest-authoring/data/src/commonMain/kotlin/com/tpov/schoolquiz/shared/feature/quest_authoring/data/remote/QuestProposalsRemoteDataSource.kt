package com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestProposalBox
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestProposalPage

/**
 * Транспорт «Предложений» (история 4.25): чтение страницы прав и решение автора.
 *
 * Оба вызова — ровно регистрации в `functions/index.js` (сторож —
 * QuestProposalsCallableNamesTest); ответ страницы приводится строгим парсером.
 */
interface QuestProposalsRemoteDataSource {
    suspend fun fetchProposals(
        box: QuestProposalBox,
        limit: Int,
        cursor: String?,
    ): QuestProposalPage

    suspend fun decideProposal(
        proposalId: String,
        decision: String,
        comment: String,
    )
}
