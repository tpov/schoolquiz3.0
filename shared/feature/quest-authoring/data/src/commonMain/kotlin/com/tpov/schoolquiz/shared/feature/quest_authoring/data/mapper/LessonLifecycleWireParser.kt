package com.tpov.schoolquiz.shared.feature.quest_authoring.data.mapper

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonHistoryEntry
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonHistoryPage
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonHistoryStatus
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonLifecycleEntry
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonLifecyclePage
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonLifecycleRating
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonLifecycleState
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonPlacement
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonRoyaltyEntry
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonRoyaltyPage
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonRoyaltySummary
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonShelf
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonTransitionCommand
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonTransitionDecision

/**
 * Строгий декодер view-конвертов `arena-lifecycle-views-v1`.
 *
 * Расширение схемы (лишние поля не проверяем, отсутствующие обязательные — отказ) и чужие версии
 * view не проезжают: проекция читается только если сервер подтвердил ровно ту редакцию, под
 * которую написан клиент.
 */
object LessonLifecycleWireParser {
    const val VIEW_VERSION = "arena-lifecycle-views-v1"
    const val TRANSITION_SCHEMA_VERSION = 1

    fun lifecyclePage(data: Any?): LessonLifecyclePage {
        val root = requiredObject(data, "lifecycle page")
        requireVersion(root, VIEW_VERSION)
        val entries = root.list(ENTRIES)
        return LessonLifecyclePage(
            entries = entries.map(::lifecycleEntry),
            nextLessonId = root.optionalId(NEXT_LESSON_ID),
        )
    }

    fun historyPage(data: Any?): LessonHistoryPage {
        val root = requiredObject(data, "history page")
        requireVersion(root, VIEW_VERSION)
        val entries = root.list(ENTRIES)
        return LessonHistoryPage(
            entries = entries.map(::historyEntry),
            nextCursor = root.optionalId(NEXT_CURSOR),
        )
    }

    fun royaltyPage(data: Any?): LessonRoyaltyPage {
        val root = requiredObject(data, "royalty page")
        requireVersion(root, VIEW_VERSION)
        val summary = root.objectField(SUMMARY)
        val summaryValue =
            LessonRoyaltySummary(
                totalCreditedNolics = summary.unsigned(TOTAL_CREDITED),
                updatedAtMs = summary.optionalLong(UPDATED_AT_MS),
            )
        val entries = root.list(ENTRIES)
        return LessonRoyaltyPage(
            summary = summaryValue,
            entries = entries.map(::royaltyEntry),
            nextCursor = root.optionalId(NEXT_CURSOR),
        )
    }

    /**
     * Квитанция `transitionLesson`. Отказ внутри квитанции — честный исход решения куратора,
     * а не транспортная ошибка: он доезжает до UI как [LessonTransitionDecision.Rejected].
     */
    fun transitionDecision(
        data: Any?,
        command: LessonTransitionCommand,
    ): LessonTransitionDecision {
        val root = requiredObject(data, "transition receipt")
        // Android callable SDK кладёт JSON-числа не только в Long (живой прогон 4.3: schemaVersion
        // приехал не Long, хотя поля view-ответов тем же SDK едут Long/Int) — сверяю через Number.
        check((root[SHEMA_VERSION_FIELD] as? Number)?.toLong() == TRANSITION_SCHEMA_VERSION.toLong()) {
            "Transition receipt has an unsupported schema version"
        }
        check(root.string(MUTATION_ID) != null) { "Transition receipt has no mutation id" }
        val status = root.string(STATUS) ?: error("Transition receipt has no status")
        val result = root.objectField(RESULT)
        val decidedAtMs = root.requiredLong(DECIDED_AT_MS)
        return when (status) {
            STATUS_APPLIED -> {
                val fromShelf = result.string(FROM_SHELF)?.let(::shelf)
                val targetShelf = result.string(TARGET_SHELF)?.let(::shelf)
                check(targetShelf == command.targetShelf) {
                    "Transition receipt names a different target shelf than the command"
                }
                LessonTransitionDecision.Applied(
                    lessonId = command.lessonId,
                    fromShelf = fromShelf,
                    targetShelf = targetShelf,
                    decidedAtMs = decidedAtMs,
                )
            }
            STATUS_REJECTED ->
                LessonTransitionDecision.Rejected(
                    lessonId = command.lessonId,
                    fromShelf = result.string(FROM_SHELF)?.let(::shelf),
                    targetShelf = command.targetShelf,
                    code = result.string(CODE) ?: error("Transition rejection has no code"),
                    reason = command.reason,
                    decidedAtMs = decidedAtMs,
                )
            else -> error("Transition receipt has an unsupported status")
        }
    }

    private fun lifecycleEntry(raw: Any?): LessonLifecycleEntry {
        val entry = requiredObject(raw, "lifecycle entry")
        val ratingRaw = entry.objectField(RATING)
        val rating =
            LessonLifecycleRating(
                sum = ratingRaw.unsigned(RATING_SUM),
                count = ratingRaw.unsigned(RATING_COUNT),
                average = ratingRaw.optionalDouble(AVERAGE),
                version = ratingRaw.unsigned(RATING_VERSION),
                updatedAtMs = ratingRaw.optionalLong(UPDATED_AT_MS),
            )
        val state = LessonLifecycleState.entries.firstOrNull { it.wire == entry.string(STATE) }
        check(state != null) { "Lifecycle entry has an unsupported state" }
        return LessonLifecycleEntry(
            lessonId = entry.requiredId(LESSON_ID),
            currentContentVersion = entry.unsigned(CURRENT_CONTENT_VERSION),
            published = entry.requiredBoolean(PUBLISHED),
            reviewApproved = entry.requiredBoolean(REVIEW_APPROVED),
            rating = rating,
            state = state,
            placement =
                entry[PLACEMENT]?.let { placementRaw ->
                    val placement = requiredObject(placementRaw, "placement")
                    LessonPlacement(
                        shelf = shelf(placement.string(SHELF)),
                        previousShelf = shelf(placement.string(PREVIOUS_SHELF)),
                        contentVersion = placement.unsigned(CONTENT_VERSION),
                        currentVersion = placement.requiredBoolean(CURRENT_VERSION),
                        placementVersion = placement.unsigned(PLACEMENT_VERSION),
                        updatedAtMs = placement.requiredLong(UPDATED_AT_MS),
                    )
                },
        )
    }

    private fun historyEntry(raw: Any?): LessonHistoryEntry {
        val entry = requiredObject(raw, "history entry")
        val status = LessonHistoryStatus.entries.firstOrNull { it.wire == entry.string(STATUS) }
        check(status != null) { "History entry has an unsupported status" }
        return LessonHistoryEntry(
            fromShelf = shelf(entry.string(FROM_SHELF)),
            targetShelf = shelf(entry.string(TARGET_SHELF)),
            status = status,
            reasonCode = entry.optionalId(REASON_CODE),
            decidedAtMs = entry.requiredLong(DECIDED_AT_MS),
        )
    }

    private fun royaltyEntry(raw: Any?): LessonRoyaltyEntry {
        val entry = requiredObject(raw, "royalty entry")
        return LessonRoyaltyEntry(
            creditedNolics = entry.unsigned(CREDITED_NOLICS),
            creditedAtMs = entry.requiredLong(CREDITED_AT_MS),
        )
    }

    private fun shelf(value: String?): LessonShelf =
        LessonShelf.entries.firstOrNull { it.wire == value } ?: error("Unknown shelf $value")

    private fun requiredObject(
        data: Any?,
        what: String,
    ): Map<*, *> = data as? Map<*, *> ?: error("$what response is not an object")

    private fun requireVersion(
        root: Map<*, *>,
        expected: String,
    ) {
        check(root.string(VIEW_VERSION_FIELD) == expected) { "Unsupported view version" }
    }

    private fun Map<*, *>.objectField(key: String): Map<*, *> =
        this[key] as? Map<*, *> ?: error("Field $key is not an object")

    private fun Map<*, *>.list(key: String): List<Any?> = this[key] as? List<*> ?: error("Field $key is not a list")

    private fun Map<*, *>.string(key: String): String? = this[key] as? String

    private fun Map<*, *>.requiredId(key: String): String {
        val value = string(key) ?: error("Field $key is missing")
        check(value.isNotBlank()) { "Field $key is blank" }
        return value
    }

    private fun Map<*, *>.optionalId(key: String): String? {
        val value = this[key] ?: return null
        return value as? String ?: error("Field $key is not a string")
    }

    private fun Map<*, *>.unsigned(key: String): Long {
        val value = requiredLong(key)
        check(value >= 0) { "Field $key is negative" }
        return value
    }

    private fun Map<*, *>.requiredLong(key: String): Long {
        val value =
            when (val raw = this[key]) {
                is Long -> raw
                is Int -> raw.toLong()
                else -> error("Field $key is not an integer")
            }
        return value
    }

    private fun Map<*, *>.optionalLong(key: String): Long? {
        val raw = this[key] ?: return null
        return when (raw) {
            is Long -> raw
            is Int -> raw.toLong()
            else -> error("Field $key is not an integer")
        }
    }

    /** Целые числа SDK отдаёт как Long/Int — средний рейтинг 2.0 не обязан ехать Double. */
    private fun Map<*, *>.optionalDouble(key: String): Double? {
        val raw = this[key] ?: return null
        return when (raw) {
            is Double -> raw
            is Long -> raw.toDouble()
            is Int -> raw.toDouble()
            else -> error("Field $key is not a number")
        }
    }

    private fun Map<*, *>.requiredBoolean(key: String): Boolean =
        this[key] as? Boolean ?: error("Field $key is not a boolean")

    private const val VIEW_VERSION_FIELD = "viewVersion"
    private const val SHEMA_VERSION_FIELD = "schemaVersion"
    private const val ENTRIES = "entries"
    private const val NEXT_LESSON_ID = "nextLessonId"
    private const val NEXT_CURSOR = "nextCursor"
    private const val SUMMARY = "summary"
    private const val LESSON_ID = "lessonId"
    private const val CURRENT_CONTENT_VERSION = "currentContentVersion"
    private const val CONTENT_VERSION = "contentVersion"
    private const val PUBLISHED = "published"
    private const val REVIEW_APPROVED = "reviewApproved"
    private const val RATING = "rating"
    private const val RATING_SUM = "sum"
    private const val RATING_COUNT = "count"
    private const val AVERAGE = "average"
    private const val RATING_VERSION = "version"
    private const val UPDATED_AT_MS = "updatedAtMs"
    private const val STATE = "state"
    private const val PLACEMENT = "placement"
    private const val SHELF = "shelf"
    private const val PREVIOUS_SHELF = "previousShelf"
    private const val CURRENT_VERSION = "currentVersion"
    private const val PLACEMENT_VERSION = "placementVersion"
    private const val STATUS = "status"
    private const val REASON_CODE = "reasonCode"
    private const val DECIDED_AT_MS = "decidedAtMs"
    private const val TOTAL_CREDITED = "totalCreditedNolics"
    private const val CREDITED_NOLICS = "creditedNolics"
    private const val CREDITED_AT_MS = "creditedAtMs"
    private const val MUTATION_ID = "mutationId"
    private const val RESULT = "result"
    private const val CODE = "code"
    private const val FROM_SHELF = "fromShelf"
    private const val TARGET_SHELF = "targetShelf"
    private const val STATUS_APPLIED = "APPLIED"
    private const val STATUS_REJECTED = "REJECTED"
}
