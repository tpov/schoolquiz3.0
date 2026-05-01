package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftQuestion
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestAuthoringBundle
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraftId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraftStatus
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraftSummary
import kotlinx.coroutines.flow.Flow

interface QuestAuthoringRepository {
    fun observeDraftSummaries(ownerUid: String): Flow<List<QuestDraftSummary>>

    fun observeDraft(draftId: QuestDraftId): Flow<QuestAuthoringBundle?>

    suspend fun getDraft(draftId: QuestDraftId): QuestAuthoringBundle?

    suspend fun getActiveDraft(ownerUid: String): QuestAuthoringBundle?

    suspend fun saveDraft(bundle: QuestAuthoringBundle): Result<Unit>

    suspend fun upsertQuestion(question: DraftQuestion): Result<Unit>

    suspend fun setDraftStatus(
        draftId: QuestDraftId,
        status: QuestDraftStatus,
        updatedAtMs: Long,
    ): Result<Unit>
}
