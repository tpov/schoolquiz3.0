package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository

import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PrivateQuizEntry
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PrivateQuizOwnResult

/**
 * Порт прогона приватного квиза (AD-29): дверь, замороженный набор, собственный результат.
 *
 * Реализация живёт в data и зовёт три серверных вызова (`startPrivateQuizAttempt`,
 * `fetchPrivateQuizRun`, `readPrivateQuizResult`). Отказы двери приезжают типизированными —
 * разбор кодов двери здесь, в домене, не повторяется.
 */
interface PrivateQuizRunRepository {
    /**
     * Открыть дверь и получить замороженный прогон.
     *
     * [startId] — ключ намерения участника:_retry того же входа обязан ехать с тем же
     * [startId] (AD-6), иначе повтор после оборванной сети потратит вторую попытку лимита.
     */
    suspend fun start(
        ownerUid: String,
        catalogId: String,
        questId: String,
        startId: String,
    ): PrivateQuizEntry

    suspend fun readOwnResult(
        ownerUid: String,
        catalogId: String,
        questId: String,
        startId: String,
    ): Result<PrivateQuizOwnResult>
}
