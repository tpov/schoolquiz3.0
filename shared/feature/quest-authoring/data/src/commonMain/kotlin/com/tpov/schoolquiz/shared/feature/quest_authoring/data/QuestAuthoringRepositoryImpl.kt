package com.tpov.schoolquiz.shared.feature.quest_authoring.data

import com.tpov.schoolquiz.shared.core.outbox.OutboxConflictResolution
import com.tpov.schoolquiz.shared.core.outbox.OutboxEntitySync
import com.tpov.schoolquiz.shared.core.outbox.OutboxState
import com.tpov.schoolquiz.shared.core.outbox.OutboxStore
import com.tpov.schoolquiz.shared.core.persistence.toOutboxEntity
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.mapper.QuestArenaSubmissionRequestMapper.toRequest
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.mapper.QuestAuthoringMapper.toDomain
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.mapper.QuestAuthoringMapper.toEntity
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.mapper.QuestAuthoringMapper.toEntityBundle
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.outbox.PrivateQuizLinkOutboxWriter
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.outbox.QuestArenaOutboxWriter
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftQuestion
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestArenaSubmission
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestAuthoringBundle
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraftConflict
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraftId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraftStatus
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraftSummary
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.provider.QuestAuthoringTimestampProvider
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.repository.QuestAuthoringRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class QuestAuthoringRepositoryImpl(
    private val local: QuestAuthoringLocalDataSource,
    private val outbox: OutboxStore,
    private val timestampProvider: QuestAuthoringTimestampProvider,
) : QuestAuthoringRepository {
    override fun observeDraftSummaries(ownerUid: String): Flow<List<QuestDraftSummary>> =
        local.observeDraftSummaries(ownerUid).map { summaries ->
            summaries.map { it.toDomain() }
        }

    override fun observeDraft(draftId: QuestDraftId): Flow<QuestAuthoringBundle?> =
        local.observeDraft(draftId.value).map { it?.toDomain() }

    override suspend fun getDraft(draftId: QuestDraftId): QuestAuthoringBundle? =
        local.getDraft(draftId.value)?.toDomain()

    override suspend fun getActiveDraft(ownerUid: String): QuestAuthoringBundle? =
        local.getActiveDraft(ownerUid)?.toDomain()

    override suspend fun activateDraft(
        ownerUid: String,
        draftId: QuestDraftId,
    ): Result<Unit> =
        runCancellable {
            local.activateDraft(ownerUid = ownerUid, draftId = draftId.value)
        }

    override suspend fun saveDraft(bundle: QuestAuthoringBundle): Result<Unit> =
        runCancellable {
            local.saveDraft(bundle.toEntityBundle())
        }

    override suspend fun upsertQuestion(question: DraftQuestion): Result<Unit> =
        runCancellable {
            local.upsertQuestion(question.toEntity())
        }

    /**
     * Подаёт черновик на арену — постановкой в общую очередь (AD-5, AD-6).
     *
     * Тело собирается здесь, в момент намерения автора, а не в момент отправки (AD-2). Прежний
     * отправитель дочитывал черновик перед самой отправкой и потому отправлял не то, что автор
     * подал, а то, во что черновик успел превратиться, пока лежал без сети.
     */
    override suspend fun queueArenaSubmission(submission: QuestArenaSubmission): Result<Unit> =
        runCancellable {
            val bundle =
                checkNotNull(local.getDraft(submission.draftId.value)?.toDomain()) {
                    "Draft ${submission.draftId.value} not found"
                }
            local.queueArenaSubmission(
                outboxRow = QuestArenaOutboxWriter.row(submission.toRequest(bundle)),
                draftId = submission.draftId.value,
                submissionId = submission.id.value,
                requestedAtMs = submission.requestedAtMs,
            )
        }

    override suspend fun queueClosePrivateQuizLink(
        ownerUid: String,
        catalogId: String,
        questId: String,
    ): Result<Unit> =
        runCancellable {
            outbox.enqueue(
                PrivateQuizLinkOutboxWriter.record(
                    ownerUid = ownerUid,
                    catalogId = catalogId,
                    questId = questId,
                    requestedAtMs = timestampProvider.nowMs(),
                ),
            )
            Unit
        }

    /**
     * Расхождение читается из очереди, а не из статуса черновика.
     *
     * Статус `CONFLICT` умеет сказать только «что-то не так»; число, поверх которого автор будет
     * отправлять заново, лежит в записи, которую отверг сервер (AD-24). Ядро при этом ни одной
     * таблицы фичи не читает — оно сравнивает непрозрачную для него ссылку на сущность (AD-7).
     */
    override fun observeDraftConflict(
        ownerUid: String,
        draftId: QuestDraftId,
    ): Flow<QuestDraftConflict?> =
        outbox.observeEntity(ownerUid, QuestArenaOutboxWriter.draftRef(draftId.value))
            .map { sync ->
                (sync as? OutboxEntitySync.Queued)?.takeIf { it.isConflicted }?.let {
                    QuestDraftConflict(
                        draftId = draftId,
                        serverVersion = it.serverVersion,
                        expectedVersion = it.expectedVersion,
                    )
                }
            }

    /**
     * Решение автора: принять серверную версию и отправить заявку заново (AD-24, AD-2).
     *
     * Новая запись и снятие прежней — одна транзакция вместе со статусом черновика (AD-23):
     * порознь автор остался бы либо с конфликтным черновиком без единой заявки в очереди, либо с
     * заявкой, о которой его черновик не знает.
     */
    override suspend fun resolveDraftConflict(
        ownerUid: String,
        draftId: QuestDraftId,
    ): Result<Unit> =
        runCancellable {
            val ref = QuestArenaOutboxWriter.draftRef(draftId.value)
            val conflicted =
                checkNotNull(outbox.findByEntityRef(ownerUid, ref)?.takeIf { it.state == OutboxState.CONFLICT }) {
                    "Draft ${draftId.value} has no conflicted submission to resolve"
                }
            // Расхождение без названной версии решением не снимается: новая заявка ушла бы с
            // пустой ожидаемой версией, а пустота для приёмника — утверждение «квеста ещё нет»,
            // то есть отправка вслепую и без сверки. Такое расхождение снимает следующий снимок
            // с сервера, а не кнопка (AD-24).
            checkNotNull(conflicted.serverVersion) {
                "Draft ${draftId.value} conflict carries no server version to resubmit over"
            }
            val nowMs = timestampProvider.nowMs()
            local.resolveArenaConflict(
                conflictedRowId = conflicted.id,
                replacement = OutboxConflictResolution.replacementFor(conflicted, nowMs).toOutboxEntity(),
                draftId = draftId.value,
                updatedAtMs = nowMs,
            )
        }

    override suspend fun setDraftStatus(
        draftId: QuestDraftId,
        status: QuestDraftStatus,
        updatedAtMs: Long,
    ): Result<Unit> =
        runCancellable {
            local.setDraftStatus(
                draftId = draftId.value,
                status = status.name,
                updatedAtMs = updatedAtMs,
            )
        }

    private suspend fun runCancellable(block: suspend () -> Unit): Result<Unit> {
        return try {
            block()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
