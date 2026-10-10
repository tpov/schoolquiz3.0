package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model

/**
 * Проекции безопасного чтения TZ06B (`arena-lifecycle-views-v1`): сервер отдаёт только разрешённые
 * автору/куратору поля статуса урока — сырое содержимое и чужие документы не покидают сервер.
 * Поля повторяют форму view-ответов; строгая проверка конверта живёт в слое data.
 */

/** Полка урока — серверный словарь `transitionLesson`, значения в точности как на проводке. */
enum class LessonShelf(val wire: String) {
    REVIEW("review"),
    ARENA("arena"),
    TOURNAMENT("tournament"),
    HOME("home"),
    RETIRED("retired"),
    REJECTED("rejected"),
}

/** Состояние жизненного цикла: под независимой проверкой или уже размещён по полкам. */
enum class LessonLifecycleState(val wire: String) {
    REVIEW("REVIEW"),
    PLACED("PLACED"),
}

/** Статус записи истории переходов. */
enum class LessonHistoryStatus(val wire: String) {
    APPLIED("APPLIED"),
    REJECTED("REJECTED"),
}

data class LessonLifecycleRating(
    val sum: Long,
    val count: Long,
    val average: Double?,
    val version: Long,
    val updatedAtMs: Long?,
)

data class LessonPlacement(
    val shelf: LessonShelf,
    val previousShelf: LessonShelf,
    /** Версия контента, закреплённая размещением, — не обязательно текущая версия урока. */
    val contentVersion: Long,
    /** Совпадает ли закреплённая версия с текущей опубликованной. */
    val currentVersion: Boolean,
    val placementVersion: Long,
    val updatedAtMs: Long,
)

data class LessonLifecycleEntry(
    val lessonId: String,
    val currentContentVersion: Long,
    val published: Boolean,
    val reviewApproved: Boolean,
    val rating: LessonLifecycleRating,
    val state: LessonLifecycleState,
    val placement: LessonPlacement?,
)

data class LessonLifecyclePage(
    val entries: List<LessonLifecycleEntry>,
    val nextLessonId: String?,
) {
    val hasNext: Boolean get() = nextLessonId != null
}

data class LessonHistoryEntry(
    val fromShelf: LessonShelf,
    val targetShelf: LessonShelf,
    val status: LessonHistoryStatus,
    val reasonCode: String?,
    val decidedAtMs: Long,
)

data class LessonHistoryPage(
    val entries: List<LessonHistoryEntry>,
    val nextCursor: String?,
) {
    val hasNext: Boolean get() = nextCursor != null
}

data class LessonRoyaltySummary(
    val totalCreditedNolics: Long,
    val updatedAtMs: Long?,
)

data class LessonRoyaltyEntry(
    val creditedNolics: Long,
    val creditedAtMs: Long,
)

data class LessonRoyaltyPage(
    val summary: LessonRoyaltySummary,
    val entries: List<LessonRoyaltyEntry>,
    val nextCursor: String?,
) {
    val hasNext: Boolean get() = nextCursor != null
}
