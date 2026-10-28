package com.tpov.schoolquiz.shared.feature.quest_authoring.data.outbox

import com.tpov.schoolquiz.shared.core.outbox.OutboxOperations
import com.tpov.schoolquiz.shared.core.outbox.OutboxRecord
import com.tpov.schoolquiz.shared.core.outbox.OutboxState
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ErrorReportProposal
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Строка общей очереди для предложения автору (AD-33, история 4.22): приём REPORT.
 *
 * Callable очередь не зовёт: единственный транспорт — приёмник мутаций (AD-6), и только там
 * ключ идемпотентности не даёт одной подаче стать двумя записями (AD-1). Локальной половины
 * у записи нет (AD-25).
 *
 * Тело — ровно поля парсера REPORT приёмника 4.21: лишнего в подаче быть не может, и вопрос
 * с ответом через неё не проедет (CAP-6). Совпадение ключей payload с белым списком сервера
 * сторожит `ProposalReportWireSentinelTest`.
 */
object ProposalOutboxWriter {
    /** Ссылка на предложение — пространство имён владеющей фичи, как и у операции (NFR4). */
    const val PROPOSAL_REF_PREFIX: String = "quest_authoring:proposal:"

    fun proposalRef(proposalId: String): String = PROPOSAL_REF_PREFIX + proposalId

    fun recordErrorReport(proposal: ErrorReportProposal): OutboxRecord =
        OutboxRecord(
            id = 0L,
            mutationId = OutboxOperations.mutationKey(OutboxOperations.SUBMIT_PROPOSAL, proposal.id),
            ownerUid = proposal.ownerUid,
            operation = OutboxOperations.SUBMIT_PROPOSAL,
            payload = errorReportPayload(proposal).toString(),
            entityRef = proposalRef(proposal.id),
            state = OutboxState.WAITING,
            createdAtMs = proposal.requestedAtMs,
        )

    private fun errorReportPayload(proposal: ErrorReportProposal) = buildJsonObject {
        put("proposalId", proposal.id)
        put("kind", KIND_REPORT)
        put("target", buildJsonObject { put("lessonId", proposal.command.lessonId) })
        put("text", proposal.command.text)
        proposal.command.ownVariant?.let { put("ownVariant", it) }
    }

    /** Вид предложения в теле — тот же словарь, что у приёмника 4.21 (PROPOSAL_KINDS). */
    const val KIND_REPORT: String = "REPORT"
}
