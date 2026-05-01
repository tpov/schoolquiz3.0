package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.use_case

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.logic.validateArenaReadiness
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ArenaReadiness
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraftId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.QuestAuthoringRepository
import kotlinx.coroutines.CancellationException

class GetArenaReadinessUseCase(
    private val repository: QuestAuthoringRepository,
) {
    suspend operator fun invoke(draftId: QuestDraftId): Result<ArenaReadiness> {
        return try {
            val bundle = requireNotNull(repository.getDraft(draftId)) {
                "Draft ${draftId.value} not found"
            }
            Result.success(validateArenaReadiness(bundle))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
