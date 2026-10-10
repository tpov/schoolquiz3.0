package com.tpov.schoolquiz.shared.feature.quest_authoring.data.outbox

import com.tpov.schoolquiz.shared.core.outbox.OutboxOperations
import com.tpov.schoolquiz.shared.core.outbox.OutboxRecord
import com.tpov.schoolquiz.shared.core.outbox.OutboxState
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonErrorReportSubmission
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Строка общей очереди для приватного отчёта об ошибке урока (AD-20, история 4.9).
 *
 * Callable очередь не зовёт: единственный транспорт — приёмник мутаций (AD-6), и только там ключ
 * идемпотентности не даёт одному отчёту стать двумя записями (AD-1). Локальной половины у записи
 * нет (AD-25): ничего в таблицах фичи не меняется, `expectedVersion` пуст — урок клиентом не
 * версионируется.
 *
 * Тело — ровно три named-поля, какие принимает серверный обработчик: лишнего в отчёте быть не
 * может, и вопрос с ответом через него не проедут.
 */
object LessonErrorReportOutboxWriter {
    /** Ссылка на отчёт — пространство имён владеющей фичи, как и у операции (NFR4). */
    const val REPORT_REF_PREFIX: String = "lesson_report:report:"

    fun reportRef(reportId: String): String = REPORT_REF_PREFIX + reportId

    fun record(submission: LessonErrorReportSubmission): OutboxRecord =
        OutboxRecord(
            id = 0L,
            mutationId = OutboxOperations.mutationKey(OutboxOperations.SUBMIT_LESSON_ERROR, submission.id),
            ownerUid = submission.ownerUid,
            operation = OutboxOperations.SUBMIT_LESSON_ERROR,
            payload = submission.toPayload().toString(),
            entityRef = reportRef(submission.id),
            state = OutboxState.WAITING,
            createdAtMs = submission.requestedAtMs,
        )

    private fun LessonErrorReportSubmission.toPayload(): JsonObject =
        buildJsonObject {
            put("reportId", id)
            put("lessonId", command.lessonId)
            put("text", command.text)
        }
}
