package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model

/**
 * Кураторский переход урока по полкам: серверный callable `setLessonPlacement`
 * (`transitionLesson` арена-сервиса). Читатели TZ06B дают куратору картинку,
 * переход — единственная пишущая половина пути куратора.
 */
data class LessonTransitionCommand(
    val lessonId: String,
    val targetShelf: LessonShelf,
    val reason: String,
)

/**
 * Решение сервера по переходу. Отказ — не исключение, а честный исход кураторского решения:
 * код отказа (REVIEW_APPROVAL_REQUIRED, LESSON_NOT_PUBLISHED, INVALID_TRANSITION…) показывается
 * куратору как результат, а история решений живёт на сервере (view `listLessonLifecycleHistory`).
 */
sealed interface LessonTransitionDecision {
    data class Applied(
        val lessonId: String,
        val fromShelf: LessonShelf?,
        val targetShelf: LessonShelf,
        val decidedAtMs: Long,
    ) : LessonTransitionDecision

    data class Rejected(
        val lessonId: String,
        val fromShelf: LessonShelf?,
        val targetShelf: LessonShelf,
        val code: String,
        val reason: String,
        val decidedAtMs: Long,
    ) : LessonTransitionDecision
}
