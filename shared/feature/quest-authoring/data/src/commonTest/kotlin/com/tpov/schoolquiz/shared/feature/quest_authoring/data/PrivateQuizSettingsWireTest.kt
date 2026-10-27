package com.tpov.schoolquiz.shared.feature.quest_authoring.data

import com.tpov.schoolquiz.shared.feature.quest_authoring.data.mapper.QuestAuthoringMapper.toEntityBundle
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.mapper.QuestArenaSubmissionRequestMapper.toRequest
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.outbox.QuestArenaOutboxWriter
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ArenaDraftDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ArenaDraftTreeDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ArenaPrivateQuizSettingsDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.PrivateQuestSnapshot
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.QuestArenaSubmissionRequest
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.PrivateQuizSettings
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.DraftLessonId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestArenaSubmission
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestAuthoringBundle
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraft
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraftId
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestDraftStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Настройки приватной ссылки едут существующим конвейером — единственной заявкой SUBMIT_ARENA
 * (AD-6, история 8.3). Здесь зажаты обе половины провода:
 *  - тело очереди несёт `draft{}.privateQuiz` (и НЕ несёт его у неприватного квеста);
 *  - приватный снимок с сервера кладёт сохранённые настройки в черновик — загрузка существующего
 *    квиза показывает их, а не дефолты заново (AD-29).
 */
class PrivateQuizSettingsWireTest {

    @Test
    fun payload_carriesPrivateQuizSettingsUnderTheServerName() {
        val payload = payloadOf(request(privateQuiz = ArenaPrivateQuizSettingsDto(3, true, 5_000L)))
        val draft = assertNotNull(payload["draft"] as? JsonObject)
        val settings = assertNotNull(draft["privateQuiz"] as? JsonObject, "настройки на проводе под своим именем")
        assertEquals("3", settings["attemptLimit"]!!.jsonPrimitive.content)
        assertEquals("true", settings["showResult"]!!.jsonPrimitive.content)
        assertEquals(5_000L, settings["linkExpiresAtMs"]!!.jsonPrimitive.longOrNull)

        // «Без срока» — явный null на проводе: отсутствие и «нет срока» сервер различает.
        val noExpiry = payloadOf(request(privateQuiz = ArenaPrivateQuizSettingsDto(1, false, null)))
        val draftNoExpiry = assertNotNull(noExpiry["draft"] as? JsonObject)
        val settingsNoExpiry = assertNotNull(draftNoExpiry["privateQuiz"] as? JsonObject)
        assertTrue(settingsNoExpiry["linkExpiresAtMs"] is JsonNull, "без срока — явный null")
    }

    @Test
    fun payload_ofAnOrdinaryQuest_omitsTheSettingsKey() {
        val payload = payloadOf(request(privateQuiz = null))
        val draft = assertNotNull(payload["draft"] as? JsonObject)
        assertNull(draft["privateQuiz"], "ключ отсутствует, а не пустой: молчание значимое")
    }

    @Test
    fun snapshot_restoresSavedSettingsIntoTheDraft() {
        val request = request(
            privateQuiz = ArenaPrivateQuizSettingsDto(attemptLimit = 2, showResult = true, linkExpiresAtMs = 7_000L),
        )
        val entity = PrivateQuestSnapshot(serverRevision = 4L, changedAtMs = 2_000L, request = request).toEntityBundle().draft
        assertEquals(2, entity.privateQuizAttemptLimit)
        assertEquals(true, entity.privateQuizShowResult)
        assertEquals(7_000L, entity.privateQuizLinkExpiresAtMs)
    }

    @Test
    fun snapshot_ofAnOrdinaryQuest_keepsTheSettingsEmpty() {
        val entity = PrivateQuestSnapshot(serverRevision = 4L, changedAtMs = 2_000L, request = request(null)).toEntityBundle().draft
        assertNull(entity.privateQuizAttemptLimit)
        assertNull(entity.privateQuizShowResult)
        assertNull(entity.privateQuizLinkExpiresAtMs)
    }

    @Test
    fun domainSettings_travelTheWholeRoundTrip() {
        val saved = PrivateQuizSettings(attemptLimit = 4, showResult = true, linkExpiresAtMs = null)
        val request = request(privateQuiz = ArenaPrivateQuizSettingsDto(saved.attemptLimit, saved.showResult, saved.linkExpiresAtMs))
        val draft = draftOf(request)
        assertEquals(saved, draft.privateQuiz, "сохранённые настройки приходят из снимка как были")
        assertTrue(draft.privateQuiz!!.validateForSave(1L), "ссылка без срока сохраняется всегда")
    }

    @Test
    fun submissionMapper_takesSettingsFromTheDraft_notDefaults() {
        val draft = draftOf(request(privateQuiz = ArenaPrivateQuizSettingsDto(2, true, 9_000L))).copy(
            privateQuiz = PrivateQuizSettings(attemptLimit = 2, showResult = true, linkExpiresAtMs = 9_000L),
        )
        val submission = QuestArenaSubmission(
            id = com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.QuestArenaSubmissionId("submission-1"),
            draftId = draft.id,
            ownerUid = "owner-1",
            expectedVersion = null,
            requestedAtMs = 10_000L,
            lessonIds = setOf(DraftLessonId("lesson-1")),
            targetShelf = "arena",
        )
        val wire = submission.toRequest(bundleOf(draft))
        assertEquals(
            ArenaPrivateQuizSettingsDto(2, true, 9_000L),
            wire.draft.privateQuiz,
            "заявка везёт настройки черновика",
        )
    }

    // ─── фикстуры ───────────────────────────────────────────────────────────────────────────────

    private fun payloadOf(request: QuestArenaSubmissionRequest): JsonObject =
        Json.parseToJsonElement(QuestArenaOutboxWriter.row(request).payload) as JsonObject

    private fun request(privateQuiz: ArenaPrivateQuizSettingsDto?): QuestArenaSubmissionRequest =
        QuestArenaSubmissionRequest(
            submissionId = "submission-1",
            draftId = "draft-1",
            ownerUid = "owner-1",
            expectedVersion = null,
            requestedAtMs = 10_000L,
            draft =
                ArenaDraftDto(
                    id = "draft-1",
                    catalogId = "private-quizzes-v1-catalog",
                    title = "Приватный квиз",
                    description = null,
                    defaultLanguage = "ru",
                    defaultDifficulty = "HARD",
                    publicQuestId = null,
                    createdAtMs = 1_000L,
                    updatedAtMs = 2_000L,
                    privateQuiz = privateQuiz,
                ),
            sections = emptyList(),
            themes = emptyList(),
            lessons = emptyList(),
            questions = emptyList(),
            targetShelf = "arena",
            targetLessonIds = emptySet(),
            draftTree = ArenaDraftTreeDto(),
        )

    private fun draftOf(request: QuestArenaSubmissionRequest): QuestDraft {
        val entity = PrivateQuestSnapshot(serverRevision = 1L, changedAtMs = 2_000L, request = request).toEntityBundle()
        return entity.let { bundle ->
            QuestDraft(
                id = QuestDraftId(bundle.draft.id),
                ownerUid = bundle.draft.ownerUid,
                catalogId = com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId(bundle.draft.catalogId),
                title = bundle.draft.title,
                description = bundle.draft.description,
                defaultLanguage = bundle.draft.defaultLanguage,
                defaultDifficulty = com.tpov.schoolquiz.shared.core.question_schema.Difficulty.valueOf(bundle.draft.defaultDifficulty),
                status = QuestDraftStatus.SYNCED,
                localRevision = 1L,
                serverRevision = 1L,
                publicQuestId = null,
                createdAtMs = bundle.draft.createdAtMs,
                updatedAtMs = bundle.draft.updatedAtMs,
                isActive = false,
                privateQuiz = bundle.draft.privateQuizColumns(),
            )
        }
    }

    private fun com.tpov.schoolquiz.shared.core.persistence.QuestDraftEntity.privateQuizColumns(): PrivateQuizSettings? {
        val limit = privateQuizAttemptLimit
        if (limit == null) return null
        return PrivateQuizSettings(
            attemptLimit = limit,
            showResult = privateQuizShowResult ?: false,
            linkExpiresAtMs = privateQuizLinkExpiresAtMs,
        )
    }

    private fun bundleOf(draft: QuestDraft): QuestAuthoringBundle =
        QuestAuthoringBundle(draft = draft, sections = emptyList(), themes = emptyList(), lessons = emptyList(), questions = emptyList())

}
