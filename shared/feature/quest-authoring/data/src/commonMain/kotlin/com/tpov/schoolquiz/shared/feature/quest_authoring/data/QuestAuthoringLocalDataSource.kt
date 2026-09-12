package com.tpov.schoolquiz.shared.feature.quest_authoring.data

import com.tpov.schoolquiz.shared.core.persistence.DraftQuestionEntity
import com.tpov.schoolquiz.shared.core.persistence.OutboxEntity
import com.tpov.schoolquiz.shared.core.persistence.QuestAuthoringDao
import com.tpov.schoolquiz.shared.core.persistence.QuestDraftEntity
import com.tpov.schoolquiz.shared.core.persistence.QuestDraftSummaryEntity
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.mapper.QuestAuthoringMapper.toEntityBundle
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.outbox.QuestArenaOutboxWriter
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.PrivateQuestSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

interface QuestAuthoringLocalDataSource {
    fun observeDraftSummaries(ownerUid: String): Flow<List<QuestDraftSummaryEntity>>

    fun observeDraft(draftId: String): Flow<QuestAuthoringEntityBundle?>

    suspend fun getDraft(draftId: String): QuestAuthoringEntityBundle?

    suspend fun getActiveDraft(ownerUid: String): QuestAuthoringEntityBundle?

    /** Делает выбранный черновик активным — тем, который откроет редактор. */
    suspend fun activateDraft(
        ownerUid: String,
        draftId: String,
    )

    suspend fun saveDraft(bundle: QuestAuthoringEntityBundle)

    suspend fun upsertQuestion(question: DraftQuestionEntity)

    /**
     * Кладёт заявку в общую очередь и запирает черновик одной транзакцией (AD-23).
     *
     * Строка приходит готовой: собирает её писатель фичи, а ядро в её тело не смотрит (AD-7).
     */
    suspend fun queueArenaSubmission(
        outboxRow: OutboxEntity,
        draftId: String,
        submissionId: String,
        requestedAtMs: Long,
    )

    /** This account's bounded, still-awaiting draft/submission pairs. */
    suspend fun findAwaitingReviewTargets(ownerUid: String): List<ArenaReviewSubmissionTarget>

    /** Заявку опубликовали: черновик выходит из ожидания рецензии и получает свой терминальный статус. */
    suspend fun applyPublication(
        ownerUid: String,
        draftId: String,
        submissionId: String,
        updatedAtMs: Long,
    ): Boolean

    suspend fun applyRejection(
        ownerUid: String,
        draftId: String,
        submissionId: String,
        reason: String?,
        updatedAtMs: Long,
    ): Boolean

    /** Safely upgrades an old awaiting row after a server-confirmed response names its submission. */
    suspend fun claimLegacyReviewSubmission(
        ownerUid: String,
        draftId: String,
        submissionId: String,
    ): Boolean

    suspend fun upsertSyncedPrivateQuest(snapshot: PrivateQuestSnapshot)

    suspend fun setDraftStatus(
        draftId: String,
        status: String,
        updatedAtMs: Long,
    )

    /**
     * Снимает отвергнутую заявку и ставит вместо неё новую — одной транзакцией вместе со статусом
     * черновика (AD-23, AD-24).
     */
    suspend fun resolveArenaConflict(
        conflictedRowId: Long,
        replacement: OutboxEntity,
        draftId: String,
        updatedAtMs: Long,
    )
}

/** Local correlation data; a null ID denotes a row created before schema version 15. */
data class ArenaReviewSubmissionTarget(
    val draftId: String,
    val activeSubmissionId: String?,
)

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
        val draft =
            dao.findActiveDraft(
                ownerUid = ownerUid,
                activeStatuses = activeDraftStatuses,
            ) ?: return null
        return buildBundle(draft)
    }

    override suspend fun activateDraft(
        ownerUid: String,
        draftId: String,
    ) {
        dao.activateDraft(ownerUid = ownerUid, draftId = draftId)
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

    override suspend fun queueArenaSubmission(
        outboxRow: OutboxEntity,
        draftId: String,
        submissionId: String,
        requestedAtMs: Long,
    ) {
        dao.queueArenaSubmission(
            outboxRow = outboxRow,
            draftId = draftId,
            submissionId = submissionId,
            updatedAtMs = requestedAtMs,
        )
    }

    override suspend fun findAwaitingReviewTargets(ownerUid: String): List<ArenaReviewSubmissionTarget> =
        dao.findDraftsAwaitingReview(ownerUid = ownerUid, limit = AWAITING_REVIEW_LIMIT).map {
            ArenaReviewSubmissionTarget(draftId = it.id, activeSubmissionId = it.activeSubmissionId)
        }

    override suspend fun applyPublication(
        ownerUid: String,
        draftId: String,
        submissionId: String,
        updatedAtMs: Long,
    ): Boolean =
        dao.settleReviewSubmission(
            draftId = draftId,
            ownerUid = ownerUid,
            submissionId = submissionId,
            status = "PUBLISHED",
            rejectionReason = null,
            updatedAtMs = updatedAtMs,
        ) == 1

    override suspend fun applyRejection(
        ownerUid: String,
        draftId: String,
        submissionId: String,
        reason: String?,
        updatedAtMs: Long,
    ): Boolean =
        dao.settleReviewSubmission(
            draftId = draftId,
            ownerUid = ownerUid,
            submissionId = submissionId,
            status = "DRAFT",
            rejectionReason = reason,
            updatedAtMs = updatedAtMs,
        ) == 1

    override suspend fun claimLegacyReviewSubmission(
        ownerUid: String,
        draftId: String,
        submissionId: String,
    ): Boolean =
        dao.claimLegacyReviewSubmission(
            draftId = draftId,
            ownerUid = ownerUid,
            submissionId = submissionId,
        ) == 1

    /**
     * Кладёт приехавший приватный квест поверх локального черновика — или объявляет расхождение.
     *
     * Расхождение меряется серверными версиями, и только ими (AD-12, AD-24): конфликт — это когда
     * сервер ушёл вперёд той версии, на которой автор остановился. Прежнее правило сравнивало
     * счётчик локальных правок с серверной версией и потому объявляло конфликт за сам факт
     * работы: десять раз сохранил черновик — десять правок против четвёртой версии, и вот тебе
     * «расхождение», которого никто не создавал.
     *
     * Грязный черновик, под которым сервер не сдвинулся, не перезаписывается: приехало то же
     * самое, что автор уже видел, а его несохранённая работа новее. Молча заменить её снимком
     * значило бы отнять ровно то, ради чего очередь и существует.
     */
    override suspend fun upsertSyncedPrivateQuest(snapshot: PrivateQuestSnapshot) {
        val incoming = snapshot.toEntityBundle()
        val existing = dao.findDraftById(incoming.draft.id)
        // Old installations have awaiting rows without an active submission ID. A private
        // snapshot is server-authored evidence of the submission that owns that row; claim it
        // before merge handling, but never overwrite an ID recorded by a newer resubmission.
        if (snapshot.request.submissionId.isNotBlank()) {
            dao.claimLegacyReviewSubmission(
                draftId = incoming.draft.id,
                ownerUid = snapshot.request.ownerUid,
                submissionId = snapshot.request.submissionId,
            )
        }
        val merge =
            privateQuestMerge(
                localStatus = existing?.status,
                knownServerRevision = existing?.serverRevision,
                incomingServerRevision = snapshot.serverRevision,
            )
        when (merge) {
            PrivateQuestMerge.CONFLICT -> {
                dao.updateDraftStatus(
                    draftId = requireNotNull(existing).id,
                    status = "CONFLICT",
                    updatedAtMs = snapshot.changedAtMs,
                )
                return
            }

            PrivateQuestMerge.KEEP_LOCAL -> return
            PrivateQuestMerge.ADOPT_SERVER_REVISION -> {
                dao.updateDraftServerRevision(
                    draftId = requireNotNull(existing).id,
                    serverRevision = requireNotNull(snapshot.serverRevision),
                    updatedAtMs = snapshot.changedAtMs,
                )
                return
            }
            PrivateQuestMerge.SAVE -> Unit
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

    override suspend fun resolveArenaConflict(
        conflictedRowId: Long,
        replacement: OutboxEntity,
        draftId: String,
        updatedAtMs: Long,
    ) {
        val submissionId =
            requireNotNull(QuestArenaOutboxWriter.submissionIdFromPayload(replacement.payload)) {
                "Replacement Arena submission does not carry a submission id"
            }
        dao.resolveArenaConflict(
            conflictedRowId = conflictedRowId,
            replacement = replacement,
            draftId = draftId,
            submissionId = submissionId,
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
        const val AWAITING_REVIEW_LIMIT = 20

        val activeDraftStatuses =
            listOf(
                "DRAFT",
                "SAVED",
                "SYNC_PENDING",
                "SYNCED",
                "CONFLICT",
            )
    }
}
