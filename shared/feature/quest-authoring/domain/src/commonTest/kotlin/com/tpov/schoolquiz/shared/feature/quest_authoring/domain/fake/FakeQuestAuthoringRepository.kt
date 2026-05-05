package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.fake

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftQuestion
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestArenaSubmission
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestAuthoringBundle
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraftId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraftStatus
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraftSummary
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.QuestAuthoringRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeQuestAuthoringRepository(
    initial: List<QuestAuthoringBundle> = emptyList(),
) : QuestAuthoringRepository {
    private val cache = MutableStateFlow(initial.associateBy { it.draft.id })
    val queuedArenaSubmissions = mutableListOf<QuestArenaSubmission>()

    override fun observeDraftSummaries(ownerUid: String): Flow<List<QuestDraftSummary>> =
        cache.map { drafts ->
            drafts.values
                .filter { it.draft.ownerUid == ownerUid }
                .map { bundle ->
                    QuestDraftSummary(
                        id = bundle.draft.id,
                        catalogId = bundle.draft.catalogId,
                        title = bundle.draft.title,
                        status = bundle.draft.status,
                        questionCount = bundle.questions.size,
                        updatedAtMs = bundle.draft.updatedAtMs,
                        isActive = bundle.draft.isActive,
                    )
                }
                .sortedWith(compareByDescending<QuestDraftSummary> { it.updatedAtMs }.thenBy { it.id.value })
        }

    override fun observeDraft(draftId: QuestDraftId): Flow<QuestAuthoringBundle?> =
        cache.map { it[draftId] }

    override suspend fun getDraft(draftId: QuestDraftId): QuestAuthoringBundle? = cache.value[draftId]

    override suspend fun getActiveDraft(ownerUid: String): QuestAuthoringBundle? =
        cache.value.values
            .filter { it.draft.ownerUid == ownerUid && it.draft.isActive }
            .maxByOrNull { it.draft.updatedAtMs }

    override suspend fun saveDraft(bundle: QuestAuthoringBundle): Result<Unit> =
        runCatching {
            cache.update { current ->
                val mutable = current.toMutableMap()
                if (bundle.draft.isActive) {
                    mutable.replaceAll { _, existing ->
                        if (existing.draft.ownerUid == bundle.draft.ownerUid) {
                            existing.copy(draft = existing.draft.copy(isActive = false))
                        } else {
                            existing
                        }
                    }
                }
                mutable[bundle.draft.id] = bundle
                mutable
            }
        }

    override suspend fun upsertQuestion(question: DraftQuestion): Result<Unit> =
        runCatching {
            cache.update { current ->
                val bundle = requireNotNull(current[question.draftId]) {
                    "Draft ${question.draftId.value} not found"
                }
                require(bundle.lessons.any { it.id == question.lessonId }) {
                    "Lesson ${question.lessonId.value} not found in draft ${question.draftId.value}"
                }
                val questions = (bundle.questions.filterNot { it.id == question.id } + question)
                    .sortedWith(compareBy<DraftQuestion> { it.lessonId.value }.thenBy { it.order }.thenBy { it.id.value })
                val updatedDraft = bundle.draft.copy(
                    updatedAtMs = maxOf(bundle.draft.updatedAtMs, question.updatedAtMs),
                    localRevision = bundle.draft.localRevision + 1,
                    status = QuestDraftStatus.DRAFT,
                )
                current + (question.draftId to bundle.copy(draft = updatedDraft, questions = questions))
            }
        }

    override suspend fun queueArenaSubmission(submission: QuestArenaSubmission): Result<Unit> =
        runCatching {
            queuedArenaSubmissions += submission
            cache.update { current ->
                val bundle = requireNotNull(current[submission.draftId]) {
                    "Draft ${submission.draftId.value} not found"
                }
                current + (
                    submission.draftId to bundle.copy(
                        draft = bundle.draft.copy(
                            status = QuestDraftStatus.REVIEW_QUEUED,
                            updatedAtMs = maxOf(bundle.draft.updatedAtMs, submission.requestedAtMs),
                        ),
                    )
                    )
            }
        }

    override suspend fun setDraftStatus(
        draftId: QuestDraftId,
        status: QuestDraftStatus,
        updatedAtMs: Long,
    ): Result<Unit> =
        runCatching {
            cache.update { current ->
                val bundle = requireNotNull(current[draftId]) { "Draft ${draftId.value} not found" }
                current + (
                    draftId to bundle.copy(
                        draft = bundle.draft.copy(
                            status = status,
                            updatedAtMs = maxOf(bundle.draft.updatedAtMs, updatedAtMs),
                            localRevision = bundle.draft.localRevision + 1,
                        ),
                    )
                    )
            }
        }

    fun snapshot(): List<QuestAuthoringBundle> =
        cache.value.values.sortedBy { it.draft.id.value }
}
