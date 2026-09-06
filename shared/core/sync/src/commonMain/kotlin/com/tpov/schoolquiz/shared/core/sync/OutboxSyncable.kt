package com.tpov.schoolquiz.shared.core.sync

import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.network.SyncFailure
import com.tpov.schoolquiz.shared.core.outbox.OutboxEngine

/**
 * Очередь как участник обычной синхронизации.
 *
 * Отдельного расписания у неё нет и не нужно: отложенные действия уезжают тогда же, когда
 * приложение и так идёт к серверу. Зависимость направлена сюда, а не наоборот — `core/outbox`
 * остаётся чистым и не знает ни про [Syncable], ни про фичи (AD-7).
 *
 * Без аккаунта делать нечего: запись принадлежит тому `uid`, который её создал, и под другим не
 * отправляется (AD-8).
 */
class OutboxSyncable(
    private val engine: OutboxEngine,
    private val currentUidProvider: suspend () -> String?,
) : Syncable {
    override suspend fun sync(): Result<Unit> {
        val uid = currentUidProvider()?.takeIf { it.isNotBlank() } ?: return Result.success(Unit)
        val outcome = runCatching { engine.drain(uid) }
        val failure = outcome.exceptionOrNull()
        // Исход шага выходит наружу его собственным результатом, а след прогона пишет [SyncRun]:
        // успех — свойство всего прогона, и шаг, объявляющий его сам, стирает ветвь ошибки соседа
        // по тому же прогону (AD-14).
        //
        // Молча вернуть успех тут особенно легко: движок намеренно не выпускает наверх неудачу
        // самой отправки (AD-22) — иначе одна отвергнутая запись глушила бы все остальные, — и
        // «drain вернулся» не значит «всё уехало». Успех — это когда ждать больше нечего.
        // Записи, ушедшие в ожидание или в карантин, означают, что проход не довёз, и ветвь
        // последней такой записи и есть то, что стоит показать. Ветвь берётся из сводки, а не
        // вычитывается обратно из очереди: там она уже разобрана в тип, а в таблице от неё
        // остался текст, собранный для журнала (AD-15).
        //
        // Отказ хранилища — «база не открылась» — движок не ловит, и он означает, что прохода не
        // было вовсе; он уезжает наверх как есть, чтобы прогон назвал его ветвь тем же способом.
        if (failure != null) return Result.failure(failure)
        val summary = outcome.getOrNull() ?: return Result.success(Unit)
        return if (summary.retried > 0 || summary.quarantined > 0) {
            Result.failure(SyncFailure(summary.lastError ?: SyncError.Unknown()))
        } else {
            Result.success(Unit)
        }
    }
}
