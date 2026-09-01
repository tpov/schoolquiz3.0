package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider

import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.RatingId

/**
 * Выдаёт идентификатор одной попытки оценить.
 *
 * Именно попытки, а не пары «игрок и урок»: идентификатор становится ключом идемпотентности
 * записи очереди, а ключ живёт ровно столько, сколько живёт намерение. Один ключ на пару значил
 * бы, что после карантина и отката оценить заново уже нельзя — ключ занят записью, которая
 * никуда не уедет.
 */
interface RatingIdProvider {
    /** Каждый вызов — новое намерение и новый идентификатор. */
    fun provide(userId: String, lessonId: LessonId): RatingId
}
