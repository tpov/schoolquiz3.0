package com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewDecisionCursor
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewDecisionMarkValue

/*
 * Строгий разбор вью «Решений» (история 4.17): та же школа, что и парсер исходов 4.16 —
 * отсутствие честного поля пусто лишь там, где сервер обязан его опустить; мусор — отказ.
 * Серверные границы повторены здесь, чтобы widen-ответ не осел на экране админа.
 */

private const val VIEW_VERSION = "quest-review-decisions-v1"
private val DECISION_STAGES = setOf("WORDS", "FACTS")
private val DECISION_VERDICTS = setOf("PASS", "FAIL")
private const val MAX_PAGE_ENTRIES = 60
private const val MAX_SOURCES = 32
private const val MAX_RATIONALE_CHARS = 8000
private const val MAX_REASON_CHARS = 2000

/** Разбор страницы: неверная версия, битые поля или превышенные границы — IllegalStateException. */
fun reviewDecisionsWirePage(data: Any?): ReviewDecisionsWirePage {
    val root = data as? Map<*, *> ?: throw IllegalStateException("Decisions response is not an object")
    check(root.string("viewVersion") == VIEW_VERSION) {
        "Decisions response has an unsupported view version"
    }
    val rawEntries = root["entries"] as? List<*> ?: throw IllegalStateException("Decisions entries are missing")
    check(rawEntries.size <= MAX_PAGE_ENTRIES) { "Decisions response exceeds its bound" }
    val entries = rawEntries.map { raw -> reviewDecisionsWireEntry(raw) }
    val hasMore = root["hasMore"] as? Boolean ?: throw IllegalStateException("Decisions hasMore flag is missing")
    val nextCursor =
        when (val rawCursor = root["nextCursor"]) {
            null -> null
            is Map<*, *> -> reviewDecisionsCursor(rawCursor)
            else -> throw IllegalStateException("Decisions cursor is not an object")
        }
    return ReviewDecisionsWirePage(entries = entries, hasMore = hasMore, nextCursor = nextCursor)
}

private fun reviewDecisionsWireEntry(raw: Any?): ReviewDecisionsWireEntry {
    val entry = raw as? Map<*, *> ?: throw IllegalStateException("Decisions entry is not an object")
    val unreadable =
        entry["unreadable"] as? Boolean ?: throw IllegalStateException(
            "Decisions unreadable flag is missing",
        )
    val verdictId = entry.requiredString("verdictId")
    if (unreadable) {
        return ReviewDecisionsWireEntry(
            unreadable = true,
            verdictId = verdictId,
            submissionId = "",
            questTitle = "",
            lessonId = "",
            lessonTitle = "",
            stage = "",
            attempt = 0,
            revision = 0L,
            model = "",
            promptVersion = "",
            sources = emptyList(),
            rationale = "",
            questionId = "",
            verdict = null,
            reason = "",
            question = null,
            markValue = null,
            markComment = "",
            markAdminUid = "",
            markUpdatedAtMs = 0L,
        )
    }
    val stage = entry.requiredString("stage")
    check(stage in DECISION_STAGES) { "Decisions entry names an unknown stage" }
    val verdict = entry.requiredString("verdict")
    check(verdict in DECISION_VERDICTS) { "Decisions entry names an unknown verdict" }
    val sources =
        (entry["sources"] as? List<*> ?: throw IllegalStateException("Decisions sources are missing"))
            .also { check(it.size <= MAX_SOURCES) { "Decisions entry names too many sources" } }
            .map { source ->
                source as? String ?: throw IllegalStateException("Decisions source is not a string")
            }
    val rationale = entry.requiredString("rationale")
    check(rationale.length <= MAX_RATIONALE_CHARS) { "Decisions rationale exceeds its bound" }
    val reason = entry.requiredString("reason")
    check(reason.length <= MAX_REASON_CHARS) { "Decisions reason exceeds its bound" }
    val attempt = entry.positiveInt("attempt")
    val revision = entry.nonNegativeLong("revision")
    val mark =
        when (val rawMark = entry["mark"]) {
            null -> null
            is Map<*, *> -> reviewDecisionsWireMark(rawMark)
            else -> throw IllegalStateException("Decisions mark is not an object")
        }
    return ReviewDecisionsWireEntry(
        unreadable = false,
        verdictId = verdictId,
        submissionId = entry.requiredString("submissionId"),
        questTitle = entry.string("questTitle") ?: "",
        lessonId = entry.requiredString("lessonId"),
        lessonTitle = entry.string("lessonTitle") ?: "",
        stage = stage,
        attempt = attempt,
        revision = revision,
        model = entry.requiredString("model"),
        promptVersion = entry.requiredString("promptVersion"),
        sources = sources,
        rationale = rationale,
        questionId = entry.requiredString("questionId"),
        verdict = verdict,
        reason = reason,
        question =
            when (val rawQuestion = entry["question"]) {
                null -> null
                is Map<*, *> -> reviewDecisionsWireQuestion(rawQuestion)
                else -> throw IllegalStateException("Decisions question is not an object")
            },
        markValue = mark?.value,
        markComment = mark?.comment ?: "",
        markAdminUid = mark?.adminUid ?: "",
        markUpdatedAtMs = mark?.updatedAtMs ?: 0L,
    )
}

private fun reviewDecisionsWireQuestion(raw: Map<*, *>): ReviewDecisionsWireQuestion {
    val question =
        ReviewDecisionsWireQuestion(
            type = raw.requiredString("type"),
            language = raw.requiredString("language"),
            text = raw.requiredString("text"),
            payload = raw.requiredString("payload"),
        )
    return question
}

private class WireMark(
    val value: ReviewDecisionMarkValue,
    val comment: String,
    val adminUid: String,
    val updatedAtMs: Long,
)

private fun reviewDecisionsWireMark(raw: Map<*, *>): WireMark {
    val value = raw.requiredString("mark")
    val markValue =
        ReviewDecisionMarkValue.entries.firstOrNull { it.name == value }
            ?: throw IllegalStateException("Decisions mark names an unknown value")
    return WireMark(
        value = markValue,
        comment = raw.string("comment") ?: "",
        adminUid = raw.requiredString("adminUid"),
        updatedAtMs = raw.nonNegativeLong("updatedAtMs"),
    )
}

private fun reviewDecisionsCursor(raw: Map<*, *>): ReviewDecisionCursor {
    val verdictId = raw.requiredString("verdictId")
    val afterQuestionId = raw.string("afterQuestionId")
    return ReviewDecisionCursor(verdictId = verdictId, afterQuestionId = afterQuestionId)
}

private fun Map<*, *>.string(key: String): String? = this[key] as? String

private fun Map<*, *>.requiredString(key: String): String =
    string(key)?.takeIf { it.isNotBlank() } ?: throw IllegalStateException("Decisions field $key is missing")

private fun Map<*, *>.positiveInt(key: String): Int {
    val raw = this[key] as? Number ?: throw IllegalStateException("Decisions field $key is missing")
    val value = raw.toInt()
    check(value >= 1) { "Decisions field $key must be positive" }
    return value
}

private fun Map<*, *>.nonNegativeLong(key: String): Long {
    val raw = this[key] as? Number ?: throw IllegalStateException("Decisions field $key is missing")
    val value = raw.toLong()
    check(value >= 0L) { "Decisions field $key must not be negative" }
    return value
}
