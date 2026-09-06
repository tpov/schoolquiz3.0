package com.tpov.schoolquiz.android.feature.quest.presentation.fake

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftQuestion
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestAuthoringBundle
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraftConflict
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraftId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraftStatus
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraftSummary
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.QuestAuthoringRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

class FakeQuestAuthoringRepository : QuestAuthoringRepository {
    private val summaries = MutableStateFlow<List<QuestDraftSummary>>(emptyList())

    override fun observeDraftSummaries(ownerUid: String): Flow<List<QuestDraftSummary>> =
        summaries

    override fun observeDraft(draftId: QuestDraftId): Flow<QuestAuthoringBundle?> =
        MutableStateFlow(null)

    override suspend fun getDraft(draftId: QuestDraftId): QuestAuthoringBundle? = null

    override suspend fun getActiveDraft(ownerUid: String): QuestAuthoringBundle? = null

    var activatedDraftId: QuestDraftId? = null

    override suspend fun activateDraft(
        ownerUid: String,
        draftId: QuestDraftId,
    ): Result<Unit> {
        activatedDraftId = draftId
        return Result.success(Unit)
    }

    override suspend fun saveDraft(bundle: QuestAuthoringBundle): Result<Unit> = Result.success(Unit)

    override suspend fun upsertQuestion(question: DraftQuestion): Result<Unit> = Result.success(Unit)

    override fun observeDraftConflict(
        ownerUid: String,
        draftId: QuestDraftId,
    ): Flow<QuestDraftConflict?> = flowOf(null)

    override suspend fun resolveDraftConflict(
        ownerUid: String,
        draftId: QuestDraftId,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun setDraftStatus(
        draftId: QuestDraftId,
        status: QuestDraftStatus,
        updatedAtMs: Long,
    ): Result<Unit> = Result.success(Unit)

    fun emit(drafts: List<QuestDraftSummary>) {
        summaries.value = drafts
    }
}
