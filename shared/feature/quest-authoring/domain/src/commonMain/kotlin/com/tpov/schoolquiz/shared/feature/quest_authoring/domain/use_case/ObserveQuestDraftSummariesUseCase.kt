package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraftSummary
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.QuestAuthoringRepository
import kotlinx.coroutines.flow.Flow

class ObserveQuestDraftSummariesUseCase(
    private val repository: QuestAuthoringRepository,
) {
    operator fun invoke(ownerUid: String): Flow<List<QuestDraftSummary>> {
        require(ownerUid.isNotBlank()) { "ownerUid must not be blank" }
        return repository.observeDraftSummaries(ownerUid)
    }
}
