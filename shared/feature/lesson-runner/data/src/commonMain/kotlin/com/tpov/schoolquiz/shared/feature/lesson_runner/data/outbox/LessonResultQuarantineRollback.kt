package com.tpov.schoolquiz.shared.feature.lesson_runner.data.outbox

import com.tpov.schoolquiz.shared.core.outbox.OutboxRecord
import com.tpov.schoolquiz.shared.core.outbox.QuarantineListener
import com.tpov.schoolquiz.shared.core.persistence.LessonAttemptDao
import com.tpov.schoolquiz.shared.core.persistence.LessonRatingLocalDao
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Что делает урок, когда его запись очереди ушла в карантин (AD-28).
 *
 * Реакция — откат локальной половины, а не пометка. Карантин терминален: запись больше не уедет
 * никогда, и оставленное локальное изменение навсегда разошлось бы с сервером — прохождение,
 * которого в статистике нет, и оценка, которую нельзя поставить снова, потому что локально она
 * уже стоит. Откат возвращает игрока в состояние «действие не сделано», где он может повторить
 * его сам.
 *
 * Ядро сюда `payload` не разбирает и таблиц урока не касается (AD-7, NFR1) — это делает владеющая
 * фича, здесь.
 */
class LessonResultQuarantineRollback(
    private val attemptDao: LessonAttemptDao,
    private val ratingDao: LessonRatingLocalDao,
) {

    /** Убирает прохождение вместе с его ответами. */
    val attempts: QuarantineListener =
        QuarantineListener { record ->
            val attemptId = record.attemptId() ?: return@QuarantineListener
            attemptDao.rollbackAttempt(attemptId)
        }

    /** Убирает отметку «оценка поставлена», чтобы игрок мог оценить квест заново. */
    val ratings: QuarantineListener =
        QuarantineListener { record ->
            val userId = record.field("userId") ?: record.ownerUid
            val lessonId = record.field("lessonId") ?: return@QuarantineListener
            ratingDao.delete(userId = userId, lessonId = lessonId)
        }

    /**
     * Идентификатор прохождения — из ссылки на сущность, а не из тела.
     *
     * Ссылку кладут одинаково и новый писатель, и миграция 5 → 6, поэтому перенесённая запись
     * откатывается тем же кодом, что и поставленная сегодня.
     */
    private fun OutboxRecord.attemptId(): String? =
        entityRef
            ?.takeIf { it.startsWith(LessonResultEntityRef.ATTEMPT_PREFIX) }
            ?.removePrefix(LessonResultEntityRef.ATTEMPT_PREFIX)
            ?.takeIf { it.isNotBlank() }
            ?: field("attemptId")

    /**
     * Поле тела записи.
     *
     * Оценка живёт локально парой «игрок и урок», а её `ratingId` в таблице не хранится, — значит
     * без тела не обойтись. Разбирать его имеет право только фича, и делает это она здесь.
     */
    private fun OutboxRecord.field(name: String): String? =
        runCatching {
            LENIENT.parseToJsonElement(payload).jsonObject[name]?.jsonPrimitive?.content
        }.getOrNull()?.takeIf { it.isNotBlank() && it != "null" }

    private companion object {
        val LENIENT = Json { ignoreUnknownKeys = true }
    }
}
