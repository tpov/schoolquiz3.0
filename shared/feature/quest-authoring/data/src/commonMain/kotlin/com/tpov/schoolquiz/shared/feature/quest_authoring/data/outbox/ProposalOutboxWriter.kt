package com.tpov.schoolquiz.shared.feature.quest_authoring.data.outbox

import com.tpov.schoolquiz.shared.core.outbox.OutboxOperations
import com.tpov.schoolquiz.shared.core.outbox.OutboxRecord
import com.tpov.schoolquiz.shared.core.outbox.OutboxState
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.CopyEditProposal
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ErrorReportProposal
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.NewLessonProposal
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
    const val KIND_COPY_EDIT: String = "COPY_EDIT"
    const val KIND_NEW_LESSON: String = "NEW_LESSON"

    /** Подача правки копии чужого урока (история 4.23, форма COPY_EDIT 4.21). */
    fun recordCopyEdit(proposal: CopyEditProposal): OutboxRecord =
        OutboxRecord(
            id = 0L,
            mutationId = OutboxOperations.mutationKey(OutboxOperations.SUBMIT_PROPOSAL, proposal.id),
            ownerUid = proposal.ownerUid,
            operation = OutboxOperations.SUBMIT_PROPOSAL,
            payload = copyEditPayload(proposal).toString(),
            entityRef = proposalRef(proposal.id),
            state = OutboxState.WAITING,
            createdAtMs = proposal.requestedAtMs,
        )

    private fun copyEditPayload(proposal: CopyEditProposal) = buildJsonObject {
        put("proposalId", proposal.id)
        put("kind", KIND_COPY_EDIT)
        put("target", buildJsonObject { put("lessonId", proposal.command.lessonId) })
        put("lessonTitle", proposal.command.lessonTitle)
        put("questions", kotlinx.serialization.json.JsonArray(proposal.command.questions.map { q ->
            buildJsonObject {
                put("id", q.id)
                put("lessonId", proposal.command.lessonId)
                put("type", q.type)
                put("language", q.language)
                put("languageLevel", q.languageLevel)
                put("difficulty", q.difficulty)
                put("order", q.order)
                put("text", q.text)
                if (q.imagePath != null) put("imagePath", q.imagePath)
                put("payload", q.payload)
            }
        }))
        put("editedQuestionIds", kotlinx.serialization.json.JsonArray(
            proposal.command.editedQuestionIds.map { kotlinx.serialization.json.JsonPrimitive(it) },
        ))
    }

    /** Подача своего урока с целевым путём в чужом курсе (история 4.24, форма NEW_LESSON 4.21). */
    fun recordNewLesson(proposal: NewLessonProposal): OutboxRecord =
        OutboxRecord(
            id = 0L,
            mutationId = OutboxOperations.mutationKey(OutboxOperations.SUBMIT_PROPOSAL, proposal.id),
            ownerUid = proposal.ownerUid,
            operation = OutboxOperations.SUBMIT_PROPOSAL,
            payload = newLessonPayload(proposal).toString(),
            entityRef = proposalRef(proposal.id),
            state = OutboxState.WAITING,
            createdAtMs = proposal.requestedAtMs,
        )

    private fun newLessonPayload(proposal: NewLessonProposal) = buildJsonObject {
        put("proposalId", proposal.id)
        put("kind", KIND_NEW_LESSON)
        put(
            "target",
            buildJsonObject {
                put("catalogId", proposal.command.target.catalogId)
                put("questId", proposal.command.target.questId)
                put("sectionId", proposal.command.target.sectionId)
                put("themeId", proposal.command.target.themeId)
            },
        )
        put(
            "lesson",
            buildJsonObject {
                put("id", proposal.command.lessonId)
                put("title", proposal.command.lessonTitle)
            },
        )
        put("questions", kotlinx.serialization.json.JsonArray(proposal.command.questions.map { q ->
            buildJsonObject {
                put("id", q.id)
                put("lessonId", proposal.command.lessonId)
                put("type", q.type)
                put("language", q.language)
                put("languageLevel", q.languageLevel)
                put("difficulty", q.difficulty)
                put("order", q.order)
                put("text", q.text)
                if (q.imagePath != null) put("imagePath", q.imagePath)
                put("payload", q.payload)
            }
        }))
    }
}
