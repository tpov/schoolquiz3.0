package com.tpov.schoolquiz.shared.feature.quest_authoring.data.outbox

import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ArenaDraftDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ArenaDraftTreeDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ArenaLessonDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ArenaReviewDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ArenaSectionDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ArenaThemeDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.QuestArenaSubmissionRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Тело мутации собирается вручную, поэтому поле, добавленное в заявку, само на провод не попадает.
 * Этот тест читает то, что реально ляжет в очередь и уедет строкой на приёмник.
 */
class QuestArenaOutboxWriterTest {

    @Test
    fun row_carriesTheWholeDraftTreeUnderItsServerName() {
        val payload = payloadOf(request())

        val tree = assertNotNull(payload["draftTree"] as? JsonObject, "полный состав черновика на проводе")
        assertEquals(listOf("section-1", "section-2"), tree.strings("sections"))
        assertEquals(listOf("theme-1", "theme-2"), tree.strings("themes"))
        assertEquals(listOf("lesson-1", "lesson-2", "lesson-3"), tree.strings("lessons"))
        // Порция уезжает отдельно и остаётся порцией.
        assertEquals(listOf("lesson-1"), (payload["targetLessonIds"] as JsonArray).map { it.jsonPrimitive.content })
    }

    @Test
    fun row_ofAnEmptyDraftStillCarriesThreeLists() {
        // Сервер сам решает, что по заявке без единого урока снимать нечего; клиент не молчит.
        val payload = payloadOf(request(draftTree = ArenaDraftTreeDto()))

        val tree = assertNotNull(payload["draftTree"] as? JsonObject)
        assertTrue(tree.strings("sections").isEmpty())
        assertTrue(tree.strings("themes").isEmpty())
        assertTrue(tree.strings("lessons").isEmpty())
    }

    private fun payloadOf(request: QuestArenaSubmissionRequest): JsonObject =
        Json.parseToJsonElement(QuestArenaOutboxWriter.row(request).payload) as JsonObject

    private fun JsonObject.strings(name: String): List<String> =
        (this[name] as JsonArray).mapNotNull { it.jsonPrimitive.contentOrNull }

    private fun request(
        draftTree: ArenaDraftTreeDto =
            ArenaDraftTreeDto(
                sections = linkedSetOf("section-1", "section-2"),
                themes = linkedSetOf("theme-1", "theme-2"),
                lessons = linkedSetOf("lesson-1", "lesson-2", "lesson-3"),
            ),
    ): QuestArenaSubmissionRequest =
        QuestArenaSubmissionRequest(
            submissionId = "submission-1",
            draftId = "draft-1",
            ownerUid = "owner-1",
            expectedVersion = 3L,
            requestedAtMs = 4L,
            draft =
                ArenaDraftDto(
                    id = "draft-1",
                    catalogId = "catalog-1",
                    title = "My draft",
                    description = null,
                    defaultLanguage = "ru",
                    defaultDifficulty = "EASY",
                    publicQuestId = null,
                    createdAtMs = 1L,
                    updatedAtMs = 2L,
                ),
            sections = listOf(ArenaSectionDto("section-1", "draft-1", "Section", 0)),
            themes = listOf(ArenaThemeDto("theme-1", "draft-1", "section-1", "Theme", 0)),
            lessons = listOf(ArenaLessonDto("lesson-1", "draft-1", "theme-1", "Lesson", 0)),
            questions = emptyList(),
            review = ArenaReviewDto(),
            targetShelf = "arena",
            targetLessonIds = linkedSetOf("lesson-1"),
            draftTree = draftTree,
        )
}
