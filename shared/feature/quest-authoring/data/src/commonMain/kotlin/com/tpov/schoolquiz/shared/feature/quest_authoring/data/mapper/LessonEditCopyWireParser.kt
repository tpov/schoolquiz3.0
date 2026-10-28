package com.tpov.schoolquiz.shared.feature.quest_authoring.data.mapper

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonEditCopy
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonEditCopyRow

/**
 * Строгий декодер конверта редактируемой копии `lesson-edit-copy-v1` (4.23, UX-23).
 *
 * Чужие версии конверта копию не строят: редактор обязан показать ровно ту проекцию, под
 * которую сервер применит правку. Ключ в копии отсутствует по построению — поле `payload`
 * строк читается как есть, его содержимое здесь не вскрывается.
 */
object LessonEditCopyWireParser {
    const val VIEW_VERSION = "lesson-edit-copy-v1"

    fun lessonEditCopy(data: Any?): LessonEditCopy {
        val root = data as? Map<*, *> ?: error("Lesson edit copy response is not an object")
        check(root.string(FIELD_VIEW) == VIEW_VERSION) { "Unsupported lesson edit copy view version" }
        val lessonId = root.string(FIELD_LESSON_ID).takeUnless { it.isNullOrBlank() }
            ?: error("Field $FIELD_LESSON_ID is missing")
        val lessonTitle = root.string(FIELD_LESSON_TITLE) ?: ""
        val baseRevision = root.unsignedInt(FIELD_BASE_REVISION)
        val rawQuestions = root[FIELD_QUESTIONS] as? List<*> ?: error("Field $FIELD_QUESTIONS is not a list")
        check(rawQuestions.isNotEmpty()) { "Lesson edit copy needs questions" }
        return LessonEditCopy(
            lessonId = lessonId,
            lessonTitle = lessonTitle,
            baseRevision = baseRevision,
            questions = rawQuestions.map(::lessonEditCopyRow),
        )
    }

    private fun lessonEditCopyRow(raw: Any?): LessonEditCopyRow {
        val row = raw as? Map<*, *> ?: error("Lesson edit copy row is not an object")
        val payload = row.string(FIELD_PAYLOAD).takeUnless { it.isNullOrBlank() }
            ?: error("Field $FIELD_PAYLOAD is missing")
        return LessonEditCopyRow(
            id = row.string(FIELD_ID).takeUnless { it.isNullOrBlank() }
                ?: error("Field $FIELD_ID is missing"),
            lessonId = row.string(FIELD_LESSON_ID).orEmpty(),
            type = row.string(FIELD_TYPE).orEmpty(),
            language = row.string(FIELD_LANGUAGE).orEmpty(),
            languageLevel = row.unsignedInt(FIELD_LANGUAGE_LEVEL),
            difficulty = row.string(FIELD_DIFFICULTY).takeUnless { it.isNullOrBlank() } ?: "EASY",
            order = row.unsignedInt(FIELD_ORDER),
            text = row.string(FIELD_TEXT).orEmpty(),
            imagePath = row.string(FIELD_IMAGE_PATH),
            payload = payload,
            editable = row[FIELD_EDITABLE] == true,
        )
    }

    private const val FIELD_VIEW = "view"
    private const val FIELD_LESSON_ID = "lessonId"
    private const val FIELD_LESSON_TITLE = "lessonTitle"
    private const val FIELD_BASE_REVISION = "baseRevision"
    private const val FIELD_QUESTIONS = "questions"
    private const val FIELD_ID = "id"
    private const val FIELD_TYPE = "type"
    private const val FIELD_LANGUAGE = "language"
    private const val FIELD_LANGUAGE_LEVEL = "languageLevel"
    private const val FIELD_DIFFICULTY = "difficulty"
    private const val FIELD_ORDER = "order"
    private const val FIELD_TEXT = "text"
    private const val FIELD_IMAGE_PATH = "imagePath"
    private const val FIELD_PAYLOAD = "payload"
    private const val FIELD_EDITABLE = "editable"

    private fun Map<*, *>.string(key: String): String? = this[key] as? String

    private fun Map<*, *>.unsignedInt(key: String): Int {
        val value = this[key] as? Long ?: this[key] as? Int ?: error("Field $key is not an integer")
        check(value in 0..Int.MAX_VALUE.toLong()) { "Field $key is out of range" }
        return value.toInt()
    }
}
