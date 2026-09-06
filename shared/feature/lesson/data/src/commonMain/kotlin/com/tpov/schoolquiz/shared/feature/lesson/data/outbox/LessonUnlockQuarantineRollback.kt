package com.tpov.schoolquiz.shared.feature.lesson.data.outbox

import com.tpov.schoolquiz.shared.core.outbox.OutboxRecord
import com.tpov.schoolquiz.shared.core.outbox.QuarantineListener
import com.tpov.schoolquiz.shared.core.persistence.UserStatsDao

/**
 * Что делает урок, когда его разблокировка ушла в карантин (AD-28).
 *
 * Здесь стоял `NoLocalEffect`: считалось, что и нолики, и набор `lessonUnlocks` пишет только
 * сервер (AD-25), и откатывать нечего. Владелец решил 2026-09-03 иначе — нолики мягкая валюта,
 * урок открывается локально сразу, — и у записи появилась локальная половина. Молчание на её
 * карантин означало бы навсегда открытый урок, за который сервер никогда не спишет: ровно то
 * расхождение, которое AD-28 запрещает.
 *
 * Последствие одно и то же для всех мутаций: локальное изменение откатывается. Нолики
 * возвращаются, урок закрывается, а карантинная строка остаётся лежать — по ней экран уроков и
 * объясняет игроку, что покупка не прошла (`observeRefusedUnlocks`). Снимется она вместе с
 * повторным нажатием: новая транзакция освободит занятый ключ.
 *
 * Ядро сюда `payload` не разбирает и таблиц фичи не касается (AD-7, NFR1) — это делает владеющая
 * фича, здесь.
 */
class LessonUnlockQuarantineRollback(
    private val userStats: UserStatsDao,
) : QuarantineListener {
    override suspend fun onQuarantined(record: OutboxRecord) {
        if (record.operation != LessonUnlockOutbox.OPERATION) return
        val lessonId = LessonUnlockOutbox.lessonIdOf(record.entityRef) ?: return
        val kind = LessonUnlockOutbox.kindOf(record.payload) ?: return
        // Списанного числа в теле нет — значит, строку положил кто-то, кто локально ничего не
        // менял. Возвращать наугад нельзя: это была бы выдача ноликов из воздуха.
        val charged = LessonUnlockOutbox.chargedNolicsOf(record.payload) ?: return
        userStats.rollbackLessonUnlock(
            uid = record.ownerUid,
            unlockKey = LessonUnlockOutbox.unlockKey(kind, lessonId),
            refundNolics = charged,
        )
    }
}
