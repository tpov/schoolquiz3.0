package com.tpov.schoolquiz.server.functions.review

import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ArenaDraftDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ArenaLessonDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ArenaQuestionDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ArenaReviewDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ArenaSectionDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.ArenaThemeDto
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.QuestArenaSubmissionRequest
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QuestArenaSubmissionProcessorTest {
    @Test
    fun processCopiesSubmissionToPrivateHierarchyAndAdminLessonQueue() = runTest {
        val store = RecordingReviewStore(profile = TrustedProfile(uid = "owner-1"))
        val processor = QuestArenaSubmissionProcessor(store, ReviewClock { 50L })

        val result = processor.process(request()).getOrThrow()

        assertEquals(
            "private/owner-1/catalogs/catalog-1/quests/draft-1",
            result.privateQuestPath,
        )
        assertEquals(listOf("admin/review/lessons/lesson-1"), result.adminLessonTaskPaths)
        assertTrue("private/owner-1/catalogs/catalog-1/quests/draft-1" in store.privateDocuments)
        assertTrue(
            "private/owner-1/catalogs/catalog-1/quests/draft-1/sections/section-1/themes/theme-1/" +
                "lessons/lesson-1/questions/question-1" in store.privateDocuments,
        )
        assertTrue("admin/review/lessons/lesson-1" in store.adminDocuments)
        assertEquals(
            mapOf("ru" to 7),
            store.adminTasks.single().checks.translatedLanguages,
        )
        assertEquals("submission-1" to 50L, store.processed.single())
    }

    @Test
    fun processFailsWhenTrustedProfileIsMissing() = runTest {
        val store = RecordingReviewStore(profile = null)
        val processor = QuestArenaSubmissionProcessor(store, ReviewClock { 50L })

        val result = processor.process(request())

        assertTrue(result.isFailure)
        assertEquals(emptyMap(), store.privateDocuments)
    }

    private class RecordingReviewStore(
        private val profile: TrustedProfile?,
    ) : QuestReviewStore {
        val privateDocuments = linkedMapOf<String, Map<String, Any?>>()
        val adminDocuments = linkedMapOf<String, Map<String, Any?>>()
        val adminTasks = mutableListOf<AdminReviewLessonTask>()
        val processed = mutableListOf<Pair<String, Long>>()

        override suspend fun readTrustedProfile(uid: String): TrustedProfile? = profile?.takeIf { it.uid == uid }

        override suspend fun writePrivateHierarchy(request: QuestArenaSubmissionRequest) {
            privateDocuments += ReviewDocumentBuilder.privateDocuments(request)
        }

        override suspend fun writeAdminReviewLessonTasks(tasks: List<AdminReviewLessonTask>) {
            adminTasks += tasks
            adminDocuments += ReviewDocumentBuilder.adminDocuments(tasks)
        }

        override suspend fun markSubmissionProcessed(
            submissionId: String,
            processedAtMs: Long,
        ) {
            processed += submissionId to processedAtMs
        }
    }

    private fun request(): QuestArenaSubmissionRequest =
        QuestArenaSubmissionRequest(
            submissionId = "submission-1",
            draftId = "draft-1",
            ownerUid = "owner-1",
            localRevision = 2L,
            requestedAtMs = 10L,
            draft =
                ArenaDraftDto(
                    id = "draft-1",
                    catalogId = "catalog-1",
                    title = "Draft",
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
            questions =
                listOf(
                    ArenaQuestionDto(
                        id = "question-1",
                        draftId = "draft-1",
                        lessonId = "lesson-1",
                        type = "SINGLE_CHOICE",
                        language = "ru",
                        languageLevel = 7,
                        difficulty = "EASY",
                        order = 0,
                        text = "Question?",
                        imagePath = null,
                        payload = """{"type":"single_choice"}""",
                        updatedAtMs = 3L,
                    ),
                ),
            review = ArenaReviewDto(translatedLanguages = mapOf("ru" to 7)),
        )
}
