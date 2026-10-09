package com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote

import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.QuestionDeliveryVerdict
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.QuestionWindowRequest
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.QuestionWindowView

/**
 * Проводная граница окна загрузки следующего вопроса (ONLINE-O2 §3; серверная половина —
 * `beginLessonQuestionWindow`/`fetchNextLessonQuestion`, история 2.6).
 *
 * Только явные вызовы, без автоматических повторов и без локальных записей: durable-половина
 * перехода — outbox (ready-ACK) и чекпоинт рановера, не этот источник. Запросы строит режимный
 * адаптер из замороженного снапшота; личность на сервере поставляет Auth, а не тело.
 */
interface QuestionWindowRemoteDataSource {
    /** Событие завершения вопроса: открыть/прочитать окно перехода (§2–§3, повтор идемпотентен). */
    suspend fun beginWindow(request: QuestionWindowRequest): Result<QuestionWindowView>

    /** Запрос выдачи следующего вопроса внутри окна (§3; повтор возвращает тот же факт). */
    suspend fun fetchNext(
        attemptId: String,
        completedQuestionIndex: Int,
    ): Result<QuestionDeliveryVerdict>
}
