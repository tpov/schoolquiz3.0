package com.tpov.schoolquiz.shared.feature.quest.domain.use_case

import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import com.tpov.schoolquiz.shared.feature.quest.domain.repository.QuestRepository

/**
 * Takes a published quest off every shelf. Until this existed, the only takedown was an operator
 * script run from a laptop with service-account credentials, hardcoded to one catalog.
 */
class RetirePublicQuestUseCase(
    private val quests: QuestRepository,
) {
    suspend operator fun invoke(questId: QuestId): Result<Unit> = quests.retirePublic(questId)
}
