package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestProposalBox
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestProposalPage
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.QuestProposalsRepository

/**
 * Страница «Предложений» (история 4.25, UX-22): чтение своей полки прав.
 *
 * Право решает сервер — чужие записи неотличимы от пустой страницы; отказ чтения — отказ
 * перечитки, а не пустота.
 */
class FetchQuestProposalsUseCase(
    private val repository: QuestProposalsRepository,
) {
    suspend operator fun invoke(
        box: QuestProposalBox,
        limit: Int = 20,
        cursor: String? = null,
    ): Result<QuestProposalPage> {
        if (limit < 1) return Result.failure(IllegalArgumentException("limit must be positive"))
        return repository.fetch(box, limit, cursor)
    }
}
