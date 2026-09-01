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

    /**
     * Записи, которые движок обязан рассмотреть в этом проходе, — по [OutboxPolicy.isDue], в
     * порядке появления.
     *
     * Это не то же самое, что «которые пора отправлять»: сюда входят и пережившие предельный
     * возраст. Отправлять их нельзя (AD-1), но решает это движок, а не выборка. Пока возраст резал
     * выборку, такая запись не попадала никуда — не уезжала, в карантин не уходила, откат не
     * звала — и висела вечно.
     */
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

    /**
     * Состояние очереди по одной сущности — для пометки её экрана (AD-14) и для разрешения
     * конфликта на нём (AD-24).
     *
     * [entityRef] для ядра непрозрачна: строку составила и разбирает та фича, которая запись
     * создала (AD-7). Ядро только сравнивает её целиком, ни одной таблицы фичи не читая.
     *
     * Запись на сущность может быть не одна: пока конфликт не разрешён, рядом может лежать
     * следующее действие. Отдаётся та, о которой игроку есть что решать, — конфликтная вперёд
     * всех, иначе самая свежая.
     */
    fun observeEntity(
        ownerUid: String,
        entityRef: String,
    ): Flow<OutboxEntitySync?>

    /** То же одним снимком — для действия, а не для показа. */
    suspend fun findByEntityRef(
        ownerUid: String,
        entityRef: String,
    ): OutboxRecord?


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

/**
 * Что очередь знает про одну сущность.
 *
 * Числа наружу отдаются целыми, а не строкой: экран черновика показывает «поверх версии 7», и
 * собирать это разбором [OutboxRecord.lastError] запрещено (AD-15).
 *
 * @property recordId запись, которой это состояние принадлежит, — по ней и разрешают конфликт.
 * @property state что с записью сейчас.
 * @property expectedVersion версия, с которой мутация уходила.
 * @property serverVersion версия, которую назвал сервер. Пусто, пока конфликта не было.
 */
data class OutboxEntitySync(
    val recordId: Long,
    val state: OutboxState,
    val expectedVersion: Long? = null,
    val serverVersion: Long? = null,
) {
    /** Разошлась ли сущность с сервером — то единственное, что требует решения игрока (AD-24). */
    val isConflicted: Boolean get() = state == OutboxState.CONFLICT
}
