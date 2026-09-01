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
) : Syncable {

    override suspend fun sync(): Result<Unit> {
        val uid = currentUidProvider()?.takeIf { it.isNotBlank() } ?: return Result.success(Unit)
        val outcome = runCatching { engine.drain(uid) }
        val failure = outcome.exceptionOrNull()
        // Исход прохода — то единственное, из чего игрок узнаёт, идёт ли синхронизация вообще:
        // до сих пор наружу не выходило ничего, кроме записи в лог (AD-14).
        if (failure == null) {
            status.recordSuccess(clock())
        } else {
            status.recordFailure(failure.syncErrorOrNull() ?: SyncError.Unknown(failure), clock())
        }
        return outcome.map { }
    }
}
