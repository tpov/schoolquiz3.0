package com.tpov.schoolquiz.shared.feature.quest_authoring.data

import com.tpov.schoolquiz.shared.core.persistence.DraftQuestionEntity
import com.tpov.schoolquiz.shared.core.persistence.QuestArenaSubmissionDao
import com.tpov.schoolquiz.shared.core.persistence.QuestArenaSubmissionEntity
import com.tpov.schoolquiz.shared.core.persistence.QuestAuthoringDao
import com.tpov.schoolquiz.shared.core.persistence.QuestDraftEntity
import com.tpov.schoolquiz.shared.core.persistence.QuestDraftSummaryEntity
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.mapper.QuestAuthoringMapper.toEntityBundle
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.PrivateQuestSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

interface QuestAuthoringLocalDataSource {
    fun observeDraftSummaries(ownerUid: String): Flow<List<QuestDraftSummaryEntity>>

    fun observeDraft(draftId: String): Flow<QuestAuthoringEntityBundle?>

    suspend fun getDraft(draftId: String): QuestAuthoringEntityBundle?

    suspend fun getActiveDraft(ownerUid: String): QuestAuthoringEntityBundle?

    suspend fun saveDraft(bundle: QuestAuthoringEntityBundle)

    suspend fun upsertQuestion(question: DraftQuestionEntity)

    suspend fun queueArenaSubmission(submission: QuestArenaSubmissionEntity)

    suspend fun findPendingArenaSubmissions(limit: Int): List<QuestArenaSubmissionEntity>

    suspend fun markArenaSubmissionFailure(
        submissionId: String,
        message: String?,
    )

    suspend fun markArenaSubmissionSent(
        submissionId: String,
        draftId: String,
        updatedAtMs: Long,
    )

    suspend fun upsertSyncedPrivateQuest(snapshot: PrivateQuestSnapshot)

    suspend fun setDraftStatus(
        draftId: String,
        status: String,
        updatedAtMs: Long,
    )
}

class QuestAuthoringLocalDataSourceImpl(
    private val dao: QuestAuthoringDao,
    private val submissionDao: QuestArenaSubmissionDao,
) : QuestAuthoringLocalDataSource {

    override fun observeDraftSummaries(ownerUid: String): Flow<List<QuestDraftSummaryEntity>> =
        dao.observeDraftSummaries(ownerUid)

    override fun observeDraft(draftId: String): Flow<QuestAuthoringEntityBundle?> =
        combine(
            dao.observeDraftEntity(draftId),
            dao.observeSections(draftId),
            dao.observeThemes(draftId),
            dao.observeLessons(draftId),
            dao.observeQuestions(draftId),
        ) { draft, sections, themes, lessons, questions ->
            draft?.let {
                QuestAuthoringEntityBundle(
                    draft = it,
                    sections = sections,
                    themes = themes,
                    lessons = lessons,
                    questions = questions,
                )
            }
        }

    override suspend fun getDraft(draftId: String): QuestAuthoringEntityBundle? {
        val draft = dao.findDraftById(draftId) ?: return null
        return buildBundle(draft)
    }

    override suspend fun getActiveDraft(ownerUid: String): QuestAuthoringEntityBundle? {
        val draft = dao.findActiveDraft(
            ownerUid = ownerUid,
            activeStatuses = activeDraftStatuses,
        ) ?: return null
        return buildBundle(draft)
    }

    override suspend fun saveDraft(bundle: QuestAuthoringEntityBundle) {
        dao.saveDraft(
            draft = bundle.draft,
            sections = bundle.sections,
            themes = bundle.themes,
            lessons = bundle.lessons,
            questions = bundle.questions,
        )
    }

    override suspend fun upsertQuestion(question: DraftQuestionEntity) {
        dao.upsertQuestion(question)
    }

    override suspend fun queueArenaSubmission(submission: QuestArenaSubmissionEntity) {
        submissionDao.queueSubmission(submission)
        dao.updateDraftStatus(
            draftId = submission.draftId,
            status = "REVIEW_QUEUED",
            updatedAtMs = submission.requestedAtMs,
        )
    }

    override suspend fun findPendingArenaSubmissions(limit: Int): List<QuestArenaSubmissionEntity> =
        submissionDao.findPending(limit)

    override suspend fun markArenaSubmissionFailure(
        submissionId: String,
        message: String?,
    ) {
        submissionDao.markFailure(submissionId, message)
    }

    override suspend fun markArenaSubmissionSent(
        submissionId: String,
        draftId: String,
        updatedAtMs: Long,
    ) {
        submissionDao.markSent(submissionId)
        dao.updateDraftStatus(
            draftId = draftId,
            status = "REVIEW_SENT",
            updatedAtMs = updatedAtMs,
        )
    }

    override suspend fun upsertSyncedPrivateQuest(snapshot: PrivateQuestSnapshot) {
        val incoming = snapshot.toEntityBundle()
        val existing = dao.findDraftById(incoming.draft.id)
        if (existing != null &&
            existing.localRevision > snapshot.serverRevision &&
            existing.status in localDirtyStatuses
        ) {
            dao.updateDraftStatus(
                draftId = existing.id,
                status = "CONFLICT",
                updatedAtMs = snapshot.changedAtMs,
            )
            return
        }
        dao.saveDraft(
            draft = incoming.draft,
            sections = incoming.sections,
            themes = incoming.themes,
            lessons = incoming.lessons,
            questions = incoming.questions,
        )
    }

    override suspend fun setDraftStatus(
        draftId: String,
        status: String,
        updatedAtMs: Long,
    ) {
        dao.updateDraftStatus(
            draftId = draftId,
            status = status,
            updatedAtMs = updatedAtMs,
        )
    }

    private suspend fun buildBundle(draft: QuestDraftEntity): QuestAuthoringEntityBundle =
        QuestAuthoringEntityBundle(
            draft = draft,
            sections = dao.findSections(draft.id),
            themes = dao.findThemes(draft.id),
            lessons = dao.findLessons(draft.id),
            questions = dao.findQuestions(draft.id),
        )

    private companion object {
        val activeDraftStatuses = listOf(
            "DRAFT",
            "SAVED",
            "SYNC_PENDING",
            "SYNCED",
            "CONFLICT",
        )
        val localDirtyStatuses = setOf(
            "DRAFT",
            "SAVED",
            "SYNC_PENDING",
            "REVIEW_QUEUED",
        )
    }
}
