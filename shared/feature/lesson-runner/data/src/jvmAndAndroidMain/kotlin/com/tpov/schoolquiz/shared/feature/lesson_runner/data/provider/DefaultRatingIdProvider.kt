package com.tpov.schoolquiz.shared.feature.lesson_runner.data.provider

import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.RatingId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider.RatingIdProvider
import java.security.MessageDigest
import java.util.UUID

/**
 * Идентификатор одной попытки оценить, а не пары «игрок и урок».
 *
 * Раньше это была голая SHA-256 от `userId:lessonId` — один и тот же идентификатор навсегда, а из
 * него один и тот же ключ идемпотентности. Пока оценка доезжает, разницы нет; ломается это на
 * карантине. Карантинная строка терминальна и из очереди не удаляется, а откат снимает локальную
 * отметку, чтобы игрок оценил заново. Он оценивает — и новая строка встаёт в очередь под тем же
 * занятым ключом, то есть не встаёт вовсе. Оценка показана поставленной и не уедет никогда.
 *
 * Поэтому ключ здесь — намерение, а не факт: каждая попытка оценить получает свой идентификатор,
 * и оценка после отката отправляется как новое действие. От двойной отправки одного намерения
 * защищает не он, а неизменность идентификатора внутри одного намерения: `LessonRating` создаётся
 * один раз, и сколько бы раз его ни отдали в очередь, ключ у него тот же.
 *
 * На сервере это ничего не удваивает: оценка игрока лежит одним документом на пару «игрок и
 * квест» (`quest_rating_submissions/{questContentKey}/ratings/{hash(uid)}`), и повторная оценка
 * его перезаписывает — считает средний балл именно он, а не журнал событий.
 *
 * Слагаемые `userId` и `lessonId` остаются в прообразе: по идентификатору в журнале должно быть
 * видно, чья это оценка и какого урока, даже когда локальной строки уже нет.
 */
class DefaultRatingIdProvider : RatingIdProvider {
    override fun provide(userId: String, lessonId: LessonId): RatingId {
        val input = "$userId:${lessonId.value}:${UUID.randomUUID()}"
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        val hex = bytes.joinToString("") { "%02x".format(it) }
        return RatingId(hex)
    }
}
