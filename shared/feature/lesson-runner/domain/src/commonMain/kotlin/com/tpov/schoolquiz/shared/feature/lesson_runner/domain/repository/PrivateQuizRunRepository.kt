package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository

import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PrivateQuizEntry
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PrivateQuizOwnResult
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PrivateQuizSubmission

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

    /**
     * Подача попытки — намерение в общую очередь (AD-6/AD-7), а не вызов: тело замораживается
     * в момент финиша, повтор ключа (он — сам [startId]) сервер отвечает сохранённым
     * терминалом. Ответа здесь нет: результат читается [readOwnResult] — текущая настройка
     * showResult применяется при чтении (AD-29).
     */
    suspend fun submitAttempt(
        ownerUid: String,
        catalogId: String,
        questId: String,
        startId: String,
        submission: PrivateQuizSubmission,
    ): Result<Unit>
}
