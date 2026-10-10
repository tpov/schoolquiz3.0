package com.tpov.schoolquiz.shared.feature.quest_authoring.data.mapper

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.InboxAnchor
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.InboxItem
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.InboxPage
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.InboxReaders

/**
 * Строгий декодер view-конвертов `inbox-views-v1`.
 *
 * Версия view обязана совпасть: проекция читается только если сервер подтвердил ровно ту
 * редакцию, под которую написан клиент. Чужие поля не проверяем, отсутствующие обязательные —
 * отказ чтения.
 */
object InboxWireParser {
    const val VIEW_VERSION = "inbox-views-v1"

    fun inboxPage(data: Any?): InboxPage {
        val root = requiredObject(data, "inbox page")
        requireVersion(root)
        val entries = root.list(ENTRIES)
        return InboxPage(
            entries = entries.map(::entry),
            nextAnchor = root[NEXT_ANCHOR]?.let { anchorRaw ->
                val anchor = requiredObject(anchorRaw, "inbox anchor")
                InboxAnchor(atMs = anchor.requiredLong(AT_MS), id = anchor.requiredId(ID))
            },
        )
    }

    private fun entry(raw: Any?): InboxItem {
        val entry = requiredObject(raw, "inbox entry")
        val readers = InboxReaders.entries.firstOrNull { it.wire == entry.string(READERS) }
            ?: error("Inbox entry has an unsupported readers marker")
        return when (entry.string(KIND)) {
            "private_error_report" -> {
                check(readers == InboxReaders.AUTHOR_ONLY) {
                    "Private error report must carry the author-only readers marker"
                }
                InboxItem.PrivateErrorReport(
                    id = entry.requiredId(ID),
                    lessonId = entry.requiredId(LESSON_ID),
                    text = entry.requiredText(TEXT),
                    createdAtMs = entry.requiredLong(CREATED_AT_MS),
                )
            }
            "lesson_comment" -> {
                check(readers == InboxReaders.PUBLIC) {
                    "Lesson comment must carry the public readers marker"
                }
                InboxItem.LessonComment(
                    id = entry.requiredId(ID),
                    lessonId = entry.requiredId(LESSON_ID),
                    text = entry.requiredText(TEXT),
                    createdAtMs = entry.requiredLong(CREATED_AT_MS),
                    authorNickname = entry.string(AUTHOR_NICKNAME) ?: "",
                )
            }
            else -> error("Inbox entry has an unsupported kind")
        }
    }

    private fun requiredObject(
        data: Any?,
        what: String,
    ): Map<*, *> = data as? Map<*, *> ?: error("$what response is not an object")

    private fun requireVersion(root: Map<*, *>) {
        check(root.string(VIEW_VERSION_FIELD) == VIEW_VERSION) { "Unsupported inbox view version" }
    }

    private fun Map<*, *>.list(key: String): List<Any?> = this[key] as? List<*> ?: error("Field $key is not a list")

    private fun Map<*, *>.string(key: String): String? = this[key] as? String

    private fun Map<*, *>.requiredId(key: String): String {
        val value = string(key) ?: error("Field $key is missing")
        check(value.isNotBlank()) { "Field $key is blank" }
        return value
    }

    private fun Map<*, *>.requiredText(key: String): String = requiredId(key)

    private fun Map<*, *>.requiredLong(key: String): Long {
        // Android callable SDK кладёт JSON-числа не только в Long (живой прогон 4.3) — сверяю
        // через Number, как и остальные парсеры семейства.
        val value = this[key] as? Number ?: error("Field $key is not a number")
        return value.toLong()
    }

    private const val VIEW_VERSION_FIELD = "viewVersion"
    private const val ENTRIES = "entries"
    private const val NEXT_ANCHOR = "nextAnchor"
    private const val KIND = "kind"
    private const val ID = "id"
    private const val LESSON_ID = "lessonId"
    private const val TEXT = "text"
    private const val CREATED_AT_MS = "createdAtMs"
    private const val READERS = "readers"
    private const val AUTHOR_NICKNAME = "authorNickname"
    private const val AT_MS = "atMs"
}
