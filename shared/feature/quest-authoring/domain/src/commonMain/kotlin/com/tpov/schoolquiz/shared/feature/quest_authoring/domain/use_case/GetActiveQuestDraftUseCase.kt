package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestAuthoringBundle
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.QuestAuthoringRepository
import kotlinx.coroutines.CancellationException

class GetActiveQuestDraftUseCase(
    private val repository: QuestAuthoringRepository,
) {
    suspend operator fun invoke(ownerUid: String): Result<QuestAuthoringBundle?> {
        return try {
            require(ownerUid.isNotBlank()) { "ownerUid must not be blank" }
            Result.success(repository.getActiveDraft(ownerUid))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
