package com.tpov.schoolquiz.shared.feature.quest.domain.use_case

import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import com.tpov.schoolquiz.shared.feature.quest.domain.repository.QuestRepository

class SetPublicQuestShelfUseCase(
    private val quests: QuestRepository,
) {
    suspend fun execute(
        questId: QuestId,
        targetShelf: String,
    ): Result<Unit> =
        runCatching {
            require(targetShelf in publicShelves) {
                "Unsupported targetShelf: $targetShelf"
            }
        }.fold(
            onSuccess = { quests.setPublicShelf(questId, targetShelf) },
            onFailure = { Result.failure(it) },
        )
}

private val publicShelves = setOf("home", "arena", "tournament", "tournamentFinal")
