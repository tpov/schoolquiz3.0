package com.tpov.schoolquiz.shared.feature.quest_authoring.data

import com.tpov.schoolquiz.shared.feature.quest_authoring.data.mapper.LessonEditCopyWireParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Строгий декодер конверта копии `lesson-edit-copy-v1` (4.23): чужая версия конверта, битая
 * строка или отсутствие payload — отказ чтения, а не копия-молчалка. Ключ в копии отсутствует
 * по построению — парсер payload не вскрывает.
 */
class LessonEditCopyWireParserTest {
    @Test
    fun `parses the envelope and its rows`() {
        val copy =
            LessonEditCopyWireParser.lessonEditCopy(
                mapOf(
                    "view" to "lesson-edit-copy-v1",
                    "lessonId" to "lesson-1",
                    "lessonTitle" to "Чужой урок",
                    "baseRevision" to 4L,
                    "questions" to
                        listOf(
                            mapOf(
                                "id" to "q-1",
                                "lessonId" to "lesson-1",
                                "type" to "SingleChoice",
                                "language" to "ru",
                                "languageLevel" to 0L,
                                "difficulty" to "EASY",
                                "order" to 0L,
                                "text" to "Вопрос?",
                                "imagePath" to null,
                                "payload" to "{\"type\":\"SingleChoiceRedacted\"}",
                                "editable" to true,
                            ),
                            mapOf(
                                "id" to "q-2",
                                "lessonId" to "lesson-1",
                                "type" to "Survey",
                                "language" to "ru",
                                "languageLevel" to 0L,
                                "difficulty" to "EASY",
                                "order" to 1L,
                                "text" to "Опрос",
                                "payload" to "{\"type\":\"Survey\"}",
                                "editable" to false,
                            ),
                        ),
                ),
            )

        assertEquals("lesson-1", copy.lessonId)
        assertEquals(4, copy.baseRevision)
        assertEquals(2, copy.questions.size)
        val editable = copy.questions.first()
        assertTrue(editable.editable)
        assertEquals("q-1", editable.id)
        val locked = copy.questions.last()
        assertFalse(locked.editable)
        assertEquals(null, locked.imagePath)
    }

    @Test
    fun `a foreign view version is refused`() {
        val error =
            assertFailsWith<IllegalStateException> {
                LessonEditCopyWireParser.lessonEditCopy(
                    mapOf(
                        "view" to "lesson-edit-copy-v2",
                        "lessonId" to "lesson-1",
                        "lessonTitle" to "",
                        "baseRevision" to 1,
                        "questions" to listOf(emptyMap<String, Any?>()),
                    ),
                )
            }
        assertEquals("Unsupported lesson edit copy view version", error.message)
    }

    @Test
    fun `a row without payload is refused rather than shown as an empty editor`() {
        assertFailsWith<IllegalStateException> {
            LessonEditCopyWireParser.lessonEditCopy(
                mapOf(
                    "view" to "lesson-edit-copy-v1",
                    "lessonId" to "lesson-1",
                    "lessonTitle" to "",
                    "baseRevision" to 1,
                    "questions" to
                        listOf(
                            mapOf(
                                "id" to "q-1",
                                "editable" to true,
                            ),
                        ),
                ),
            )
        }
    }

    @Test
    fun `an empty question list is refused - a lesson copy always has questions`() {
        assertFailsWith<IllegalStateException> {
            LessonEditCopyWireParser.lessonEditCopy(
                mapOf(
                    "view" to "lesson-edit-copy-v1",
                    "lessonId" to "lesson-1",
                    "lessonTitle" to "",
                    "baseRevision" to 1,
                    "questions" to emptyList<Any?>(),
                ),
            )
        }
    }
}
