package com.tpov.schoolquiz.shared.feature.quest_authoring.data.outbox

import com.tpov.schoolquiz.shared.core.outbox.OutboxRecord
import com.tpov.schoolquiz.shared.core.outbox.QuarantineListener
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.QuestAuthoringLocalDataSource
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.provider.QuestAuthoringTimestampProvider

/**
 * Что делает авторство квеста, когда заявка на арену ушла в карантин (AD-28).
 *
 * Локальная половина заявки — статус черновика `REVIEW_QUEUED`: черновик заперт на
 * редактирование, потому что «уже подан». Карантин терминален, заявка не уедет никогда, и
 * оставленный статус запер бы черновик навсегда. Откат возвращает его в `DRAFT` вместе с
 * причиной, по которой заявка не дошла, — тем же путём, каким кладётся отказ рецензента.
 */
class QuestArenaQuarantineRollback(
    private val local: QuestAuthoringLocalDataSource,
    private val timestampProvider: QuestAuthoringTimestampProvider,
) : QuarantineListener {
    override suspend fun onQuarantined(record: OutboxRecord) {
        val draftId =
            record.entityRef
                ?.takeIf { it.startsWith(QuestArenaOutboxWriter.DRAFT_REF_PREFIX) }
                ?.removePrefix(QuestArenaOutboxWriter.DRAFT_REF_PREFIX)
                ?.takeIf { it.isNotBlank() }
                ?: return
        val submissionId = QuestArenaOutboxWriter.submissionIdFromPayload(record.payload) ?: return
        local.applyRejection(
            ownerUid = record.ownerUid,
            draftId = draftId,
            submissionId = submissionId,
            reason = record.lastError,
            updatedAtMs = timestampProvider.nowMs(),
        )
    }
}
