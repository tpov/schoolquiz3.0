package com.tpov.schoolquiz.shared.feature.quest_authoring.data.outbox

import com.tpov.schoolquiz.shared.core.outbox.OutboxNamespace
import com.tpov.schoolquiz.shared.core.outbox.OutboxOperations
import com.tpov.schoolquiz.shared.core.outbox.OutboxState
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonErrorReportSubmission
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.SubmitLessonErrorReportCommand
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Строка очереди для приватного отчёта (AD-20): тело — ровно три named-поля, ключ выводится из
 * идентификатора намерения, ссылка живёт в пространстве имён операции (NFR4).
 */
class LessonErrorReportOutboxWriterTest {
    private val submission =
        LessonErrorReportSubmission(
            id = "lesson-error-abc123",
            ownerUid = "author-uid",
            requestedAtMs = 1_760_000_000_000,
            command = SubmitLessonErrorReportCommand(
                lessonId = "ac_0ecb_lesson_9",
                text = "Question two never accepts the right answer",
            ),
        )

    @Test
    fun `record carries the registry operation and the id-derived mutation key`() {
        val record = LessonErrorReportOutboxWriter.record(submission)

        assertEquals(OutboxOperations.SUBMIT_LESSON_ERROR, record.operation)
        assertEquals(
            OutboxOperations.mutationKey(OutboxOperations.SUBMIT_LESSON_ERROR, "lesson-error-abc123"),
            record.mutationId,
        )
        assertEquals("author-uid", record.ownerUid)
        assertEquals(OutboxState.WAITING, record.state)
        assertNull(record.expectedVersion, "урок клиентом не версионируется — версии в очереди нет")
    }

    @Test
    fun `entity ref lives in the operation namespace`() {
        val record = LessonErrorReportOutboxWriter.record(submission)

        OutboxNamespace.requireSameNamespace(record.operation, record.entityRef)
        assertEquals("lesson_report:report:lesson-error-abc123", record.entityRef)
    }

    @Test
    fun `payload names exactly the three server fields and nothing else`() {
        val payload = LessonErrorReportOutboxWriter.record(submission).payload

        val body = kotlinx.serialization.json.Json.parseToJsonElement(payload).jsonObject
        assertEquals(setOf("reportId", "lessonId", "text"), body.keys)
        assertEquals("lesson-error-abc123", body["reportId"]!!.jsonPrimitive.content)
        assertEquals("ac_0ecb_lesson_9", body["lessonId"]!!.jsonPrimitive.content)
        assertEquals("Question two never accepts the right answer", body["text"]!!.jsonPrimitive.content)
        assertTrue("question" !in body && "answer" !in body, "полей вопросов в отчёте нет по построению")
    }
}
