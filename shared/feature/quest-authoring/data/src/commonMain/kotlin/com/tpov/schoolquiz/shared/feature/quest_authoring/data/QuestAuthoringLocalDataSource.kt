package com.tpov.schoolquiz.shared.feature.quest_authoring.data

import com.tpov.schoolquiz.shared.core.persistence.DraftQuestionEntity
import com.tpov.schoolquiz.shared.core.persistence.QuestAuthoringDao
import com.tpov.schoolquiz.shared.core.persistence.QuestDraftEntity
import com.tpov.schoolquiz.shared.core.persistence.QuestDraftSummaryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

interface QuestAuthoringLocalDataSource {
    fun observeDraftSummaries(ownerUid: String): Flow<List<QuestDraftSummaryEntity>>

    fun observeDraft(draftId: String): Flow<QuestAuthoringEntityBundle?>

    suspend fun getDraft(draftId: String): QuestAuthoringEntityBundle?

    suspend fun getActiveDraft(ownerUid: String): QuestAuthoringEntityBundle?

    suspend fun saveDraft(bundle: QuestAuthoringEntityBundle)

    suspend fun upsertQuestion(question: DraftQuestionEntity)

    suspend fun setDraftStatus(
        draftId: String,
        status: String,
        updatedAtMs: Long,
    )
}

class QuestAuthoringLocalDataSourceImpl(
    private val dao: QuestAuthoringDao,
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
            "SYNC_PENDING",
            "SYNCED_PRIVATE",
            "CONFLICT",
        )
    }
}
