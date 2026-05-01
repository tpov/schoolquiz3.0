package com.tpov.schoolquiz.android.feature.quest.presentation.fake

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftQuestion
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestAuthoringBundle
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraftId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraftStatus
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraftSummary
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.QuestAuthoringRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeQuestAuthoringRepository : QuestAuthoringRepository {
    private val summaries = MutableStateFlow<List<QuestDraftSummary>>(emptyList())

    override fun observeDraftSummaries(ownerUid: String): Flow<List<QuestDraftSummary>> =
        summaries

    override fun observeDraft(draftId: QuestDraftId): Flow<QuestAuthoringBundle?> =
        MutableStateFlow(null)

    override suspend fun getDraft(draftId: QuestDraftId): QuestAuthoringBundle? = null

    override suspend fun getActiveDraft(ownerUid: String): QuestAuthoringBundle? = null

    override suspend fun saveDraft(bundle: QuestAuthoringBundle): Result<Unit> = Result.success(Unit)

    override suspend fun upsertQuestion(question: DraftQuestion): Result<Unit> = Result.success(Unit)

    override suspend fun setDraftStatus(
        draftId: QuestDraftId,
        status: QuestDraftStatus,
        updatedAtMs: Long,
    ): Result<Unit> = Result.success(Unit)

    fun emit(drafts: List<QuestDraftSummary>) {
        summaries.value = drafts
    }
}
