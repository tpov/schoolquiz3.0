package com.tpov.schoolquiz.shared.feature.quest_authoring.data

import com.tpov.schoolquiz.shared.core.outbox.OutboxOperations
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.outbox.ProposalOutboxWriter
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ErrorReportProposal
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.NewLessonProposal
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.NewLessonProposalQuestion
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.NewLessonProposalTarget
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.SubmitNewLessonProposalCommand
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.SubmitErrorReportProposalCommand
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * Проволочный сторож REPORT-подачи (история 4.22): тело, которое собирает клиентский писатель,
 * и белый список, который принимает приёмник 4.21, обязаны совпадать по ключам.
 *
 * Ловушка не гипотетическая: приёмник 4.21 отвергает лишнее поле окончательно (AD-20), и такая
 * подача навсегда остаётся в карантине очереди. Ловим это здесь, а не в бою. JVM-тест, а не
 * общий: файл с диска общий код не видит (OutboxOperationsRegistryTest — тот же приём).
 */
class ProposalReportWireSentinelTest {
    private val serverParser: String by lazy {
        val file = File("../../../../functions/lesson-proposals.js")
        assertTrue(file.exists(), "нет functions/lesson-proposals.js: ${file.absolutePath}")
        file.readText()
    }

    private fun writerRecord(ownVariant: String?) = ProposalOutboxWriter.recordErrorReport(
        ErrorReportProposal(
            id = "lesson-proposal-1",
            ownerUid = "owner",
            requestedAtMs = 1L,
            command = SubmitErrorReportProposalCommand(
                lessonId = "lesson-1",
                text = "текст отчёта",
                ownVariant = ownVariant,
            ),
        ),
    )

    private fun writerPayload(ownVariant: String?): JsonObject =
        Json.parseToJsonElement(writerRecord(ownVariant).payload).jsonObject

    @Test
    fun `writer operation is the server mutation name`() {
        assertEquals("quest_authoring.SUBMIT_PROPOSAL", OutboxOperations.SUBMIT_PROPOSAL)
    }

    @Test
    fun `report payload carries exactly the keys the server parser accepts`() {
        assertEquals(
            setOf("proposalId", "kind", "target", "text"),
            writerPayload(ownVariant = null).keys,
            "ключи payload REPORT обязаны совпадать с белым списком парсера 4.21",
        )
        assertEquals(
            setOf("proposalId", "kind", "target", "text", "ownVariant"),
            writerPayload(ownVariant = "свой вариант").keys,
            "свой вариант — единственное необязательное поле поверх базовых",
        )
    }

    @Test
    fun `report payload shape matches the server expectations`() {
        val record = writerRecord(ownVariant = "вариант")
        assertEquals("quest_authoring:proposal:lesson-proposal-1", record.entityRef)
        val payload = writerPayload(ownVariant = "вариант")
        assertEquals("REPORT", (payload.getValue("kind") as JsonPrimitive).content)
        assertEquals(
            JsonObject(mapOf("lessonId" to JsonPrimitive("lesson-1"))),
            payload.getValue("target").jsonObject,
            "цель REPORT — target.lessonId, не голый lessonId",
        )
    }

    @Test
    fun `server parser names exactly the report fields the writer sends`() {
        // Белый список приёмника: proposalId, kind, target.lessonId, text, ownVariant — каждый
        // назван в парсере REPORT; лишних полей там не появляется.
        for (marker in listOf(
            "docId(body.proposalId",
            "PROPOSAL_KINDS.includes(body.kind)",
            "body.target && body.target.lessonId",
            "nonBlank(body.text)",
            "body.ownVariant",
        )) {
            assertTrue(serverParser.contains(marker), "парсер REPORT обязан читать $marker")
        }
        val textBound = Regex("MAX_REPORT_TEXT_CHARS = (\\d+)").find(serverParser)
        assertEquals(
            "2000",
            textBound?.groupValues?.get(1),
            "граница текста сервера — та, что дублирует use case",
        )
    }

    // ─── COPY_EDIT (история 4.23) ────────────────────────────────────────────────────────────────

    private fun copyEditRecord() = ProposalOutboxWriter.recordCopyEdit(
        com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.CopyEditProposal(
            id = "lesson-proposal-2",
            ownerUid = "owner",
            requestedAtMs = 1L,
            command = com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.SubmitCopyEditProposalCommand(
                lessonId = "lesson-1",
                lessonTitle = "Чужой урок",
                questions =
                    listOf(
                        com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.CopyEditProposalQuestion(
                            id = "q-1",
                            type = "SingleChoice",
                            language = "ru",
                            languageLevel = 0,
                            difficulty = "EASY",
                            order = 0,
                            text = "Вопрос?",
                            imagePath = null,
                            payload = "{\"type\":\"SingleChoice\",\"correctOptionId\":\"ri-0\"}",
                        ),
                    ),
                editedQuestionIds = listOf("q-1"),
            ),
        ),
    )

    @Test
    fun `copy edit payload carries exactly the keys the server parser accepts`() {
        val payload = Json.parseToJsonElement(copyEditRecord().payload).jsonObject
        assertEquals(
            setOf("proposalId", "kind", "target", "lessonTitle", "questions", "editedQuestionIds"),
            payload.keys,
            "ключи payload COPY_EDIT обязаны совпадать с белым списком парсера 4.21",
        )
        val questions = payload.getValue("questions")
        assertTrue(questions is kotlinx.serialization.json.JsonArray, "questions COPY_EDIT — массив")
        assertTrue(
            questions.toString().contains("\"lessonId\""),
            "каждый вопрос COPY_EDIT несёт lessonId цели — парсер 4.21 сверяет его",
        )
        assertEquals("COPY_EDIT", (payload.getValue("kind") as JsonPrimitive).content)
    }

    @Test
    fun `server parser names exactly the copy edit fields the writer sends`() {
        for (marker in listOf(
            "nonBlank(body.lessonTitle)",
            "parseProposalQuestions(body.questions, targetLessonId)",
            "body.editedQuestionIds",
        )) {
            assertTrue(serverParser.contains(marker), "парсер COPY_EDIT обязан читать $marker")
        }
        val titleBound = Regex("MAX_LESSON_TITLE_CHARS = (\\d+)").find(serverParser)
        assertEquals(
            "200",
            titleBound?.groupValues?.get(1),
            "граница заголовка сервера — та, что дублирует use case",
        )
    }
}
