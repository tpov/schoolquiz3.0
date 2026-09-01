package com.tpov.schoolquiz.shared.core.sync

import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.network.syncErrorOrNull
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
    private val status: SyncStatusRepository,
    private val clock: () -> Long,
    /**
     * Чем кончилась последняя неудача, если очередь умеет это рассказать.
     *
     * Необязательно: движок сообщает только числа, а ветвь ошибки лежит в записи. Без пробы
     * показывается «неизвестно» — это хуже, чем правда, но лучше, чем ложный успех.
     */
    private val statusProbe: (suspend (String) -> SyncError?)? = null,
) : Syncable {

    override suspend fun sync(): Result<Unit> {
        val uid = currentUidProvider()?.takeIf { it.isNotBlank() } ?: return Result.success(Unit)
        val outcome = runCatching { engine.drain(uid) }
        val failure = outcome.exceptionOrNull()
        // Исход прохода — то единственное, из чего игрок узнаёт, идёт ли синхронизация вообще
        // (AD-14). Считать успехом сам факт, что drain вернулся, нельзя: он по контракту не
        // бросает, поэтому ошибка сюда не долетает никогда, и «последняя ошибка» осталась бы
        // пустой при любом числе неудач.
        //
        // Успех — это когда ничего не осталось ждать. Записи, ушедшие в ожидание или в карантин,
        // означают, что проход не довёз, и причина последней такой записи и есть то, что стоит
        // показать.
        val summary = outcome.getOrNull()
        when {
            failure != null -> status.recordFailure(failure.syncErrorOrNull() ?: SyncError.Unknown(failure), clock())
            summary == null -> Unit
            summary.retried > 0 || summary.quarantined > 0 ->
                status.recordFailure(lastErrorOf(uid) ?: SyncError.Unknown(), clock())
            else -> status.recordSuccess(clock())
        }
        return outcome.map { }
    }

    /**
     * Чем кончилась последняя неудавшаяся запись.
     *
     * Движок знает только числа, а показать игроку надо ветвь. Берём её из самой очереди: там
     * причина уже разобрана и записана.
     */
    private suspend fun lastErrorOf(uid: String): SyncError? = statusProbe?.invoke(uid)
}
