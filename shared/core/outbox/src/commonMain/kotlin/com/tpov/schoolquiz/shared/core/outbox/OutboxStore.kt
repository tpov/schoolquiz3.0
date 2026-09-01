package com.tpov.schoolquiz.shared.core.outbox

import kotlinx.coroutines.flow.Flow

/**
 * Хранилище очереди. Контракт живёт в общем коде, реализация — на Room.
 *
 * Ядро не знает ни про базу, ни про то, что лежит в [OutboxRecord.payload]: одна таблица на все
 * типы действий (AD-5), и новый тип не требует ни правки схемы, ни правки этого интерфейса.
 */
interface OutboxStore {
    /**
     * Кладёт запись и возвращает её с присвоенным `id`.
     *
     * Вызывается внутри той же транзакции, что и локальное изменение (AD-23) — иначе действие
     * показано игроку как сделанное и никогда не доедет.
     */
    suspend fun enqueue(record: OutboxRecord): OutboxRecord

    /** Записи, которые пора отправлять: по [OutboxPolicy.isDue], в порядке появления. */
    suspend fun dueRecords(
        ownerUid: String,
        nowMs: Long,
        limit: Int,
    ): List<OutboxRecord>

    /** Применяет решение по записи после попытки. */
    suspend fun apply(
        id: Long,
        decision: OutboxDecision,
    )

    /**
     * Убирает отправленную запись.
     *
     * Очередь — это очередь, а не архив (AD-4): история остаётся на сервере. Форма надгробия,
     * доставшаяся от очереди оценок, отменена.
     */
    suspend fun remove(id: Long)

    /** Сводка для показа игроку (AD-14): по числу на каждое состояние, а не одно на все. */
    fun observeCounts(ownerUid: String): Flow<OutboxCounts>

    /** Записи в карантине — чтобы владеющая фича разобрала последствие (AD-28). */
    suspend fun quarantined(ownerUid: String): List<OutboxRecord>

    /**
     * Сливает очередь перед сменой аккаунта (AD-8).
     *
     * Возвращает, сколько осталось неотправленным: если не ноль, игрока предупреждают словами,
     * а не молча теряют его действия.
     */
    suspend fun countPending(ownerUid: String): Int
}

/**
 * Сколько записей в каком состоянии.
 *
 * Числа раздельные намеренно: одно «ожидает» на все состояния скрыло бы и конфликт, и карантин —
 * ровно то, что AD-14 требует показывать.
 */
data class OutboxCounts(
    val waiting: Int = 0,
    val waitingPrecondition: Int = 0,
    val conflicted: Int = 0,
    val quarantined: Int = 0,
) {
    /** Сколько ещё поедет само. */
    val pending: Int get() = waiting + waitingPrecondition

    /** Сколько требует внимания — игрока или фичи. */
    val stuck: Int get() = conflicted + quarantined
}
