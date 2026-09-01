package com.tpov.schoolquiz.shared.core.sync

import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.outbox.OutboxCounts
import kotlinx.coroutines.flow.Flow

/**
 * Что игрок может узнать про синхронизацию.
 *
 * Сегодня наружу не выходит ничего, кроме `Log.w`: приложение не сообщает ни что четыре действия
 * ждут отправки, ни что одно из них уже никогда не уедет. Узнать это нельзя никак.
 *
 * Числа раздельные намеренно (AD-14). Одно «ожидает» на все состояния скрыло бы и конфликт, и
 * карантин — а это ровно те два случая, где игроку нужно что-то сделать.
 *
 * @property lastSuccessAtMs когда синхронизация последний раз прошла целиком. Ноль — ни разу.
 * @property counts сколько записей в каком состоянии.
 * @property lastError чем кончилась последняя неудачная попытка, если она была.
 */
data class SyncStatus(
    val lastSuccessAtMs: Long = 0L,
    val counts: OutboxCounts = OutboxCounts(),
    val lastError: SyncError? = null,
) {
    /** Есть ли что-то, что уедет само. */
    val hasPending: Boolean get() = counts.pending > 0

    /**
     * Есть ли то, что само не рассосётся.
     *
     * Карантин терминален, конфликт ждёт решения игрока — и то и другое требует внимания, в
     * отличие от очереди, которая просто ждёт сети.
     */
    val needsAttention: Boolean get() = counts.stuck > 0

    /** Синхронизировались ли хоть раз. Отличается от «синхронизировались только что». */
    val hasEverSucceeded: Boolean get() = lastSuccessAtMs > 0L
}

/**
 * Состояние синхронизации наружу.
 *
 * Auth-scoped по инварианту 8: поток пересоздаётся при смене аккаунта, потому что и очередь, и
 * её счётчики принадлежат конкретному `uid` (AD-8). Пустой поток для отсутствующего аккаунта
 * запрещён — гость видит нули, а не отсутствие данных.
 */
interface SyncStatusRepository {
    fun observeStatus(): Flow<SyncStatus>

    /** Отмечает удачный проход. */
    suspend fun recordSuccess(atMs: Long)

    /** Отмечает неудачу — с типом, а не с текстом (AD-15). */
    suspend fun recordFailure(
        error: SyncError,
        atMs: Long,
    )
}
