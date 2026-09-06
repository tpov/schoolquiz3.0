package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Доступ к очереди.
 *
 * Выборка живёт здесь, а не в ядре, ровно потому, что её условие — часть запроса (AD-22). Тащить в
 * память всю таблицу, чтобы отфильтровать её в Kotlin, значит поставить размер очереди в
 * зависимость от памяти телефона.
 *
 * Выборок две, и обе обязательны: [due] отдаёт то, что пора отправлять, [expired] — то, что
 * отправлять уже нельзя, но и оставить нельзя. Пока была только первая, запись старше предельного
 * возраста не попадала ни в один запрос и висела вечно.
 */
@Dao
interface OutboxDao {
    /**
     * Ставит запись в очередь.
     *
     * [OnConflictStrategy.IGNORE] держит обещание AD-2: повторная постановка того же
     * `mutation_id` не создаёт вторую запись. Возвращает `-1`, если запись уже была.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: OutboxEntity): Long

    @Query("SELECT * FROM outbox WHERE id = :id")
    suspend fun findById(id: Long): OutboxEntity?

    @Query("SELECT * FROM outbox WHERE mutation_id = :mutationId")
    suspend fun findByMutationId(mutationId: String): OutboxEntity?

    /** То, что пора отправлять: дозревшее по паузе и не пережившее предельный возраст. */
    @Query(
        """
        SELECT * FROM outbox
        WHERE owner_uid = :ownerUid
          AND state IN ('WAITING', 'WAITING_PRECONDITION')
          AND next_retry_at_ms <= :nowMs
          AND (:nowMs - created_at_ms) < :maxAgeMs
        ORDER BY created_at_ms ASC
        LIMIT :limit
        """,
    )
    suspend fun due(
        ownerUid: String,
        nowMs: Long,
        maxAgeMs: Long,
        limit: Int,
    ): List<OutboxEntity>

    /**
     * То, что пережило предельный возраст.
     *
     * Отправлять такую запись нельзя (AD-1), но она обязана дойти до карантина и до отката, иначе
     * останется зомби: не уедет, в карантин не попадёт, реакцию фичи не позовёт. Пауза до
     * следующей попытки здесь не смотрится намеренно — ждать нечего, попытки не будет.
     */
    @Query(
        """
        SELECT * FROM outbox
        WHERE owner_uid = :ownerUid
          AND state IN ('WAITING', 'WAITING_PRECONDITION')
          AND (:nowMs - created_at_ms) >= :maxAgeMs
        ORDER BY created_at_ms ASC
        LIMIT :limit
        """,
    )
    suspend fun expired(
        ownerUid: String,
        nowMs: Long,
        maxAgeMs: Long,
        limit: Int,
    ): List<OutboxEntity>

    /**
     * Записывает решение по записи — вместе с версией, названной сервером.
     *
     * `server_version` пишется здесь, а не отдельным запросом: разрешение конфликта опирается на
     * конкретное число, и оно обязано лечь на диск тем же движением, что и состояние `CONFLICT`.
     * Двумя запросами между ними помещается смерть процесса — и запись пережила бы перезапуск в
     * конфликте без версии, то есть неразрешимой (AD-24).
     */
    @Query(
        """
        UPDATE outbox
        SET state = :state,
            next_retry_at_ms = :nextRetryAtMs,
            attempt_count = :attemptCount,
            last_error = :lastError,
            server_version = :serverVersion
        WHERE id = :id
        """,
    )
    suspend fun applyDecision(
        id: Long,
        state: String,
        nextRetryAtMs: Long,
        attemptCount: Int,
        lastError: String?,
        serverVersion: Long?,
    )

    /**
     * Запись, о которой экрану сущности есть что сказать.
     *
     * Записей на одну сущность может быть несколько: пока конфликт ждёт решения, рядом успевает
     * лечь следующее действие. Показывать надо ту, по которой игроку решать, — поэтому конфликтная
     * идёт вперёд всех, за ней карантинная, и только среди равных выигрывает самая свежая.
     *
     * Карантин стоит выше ожидания намеренно: он терминален, и более свежая ждущая запись,
     * встав перед ним, спрятала бы от игрока единственное состояние, из которого само ничего не
     * выйдет. Порядок здесь тот же, что у `FakeOutboxStore`, — разъедутся, и тесты фич начнут
     * утверждать не то, что делает база.
     *
     * Выбираются четыре колонки, а не строка целиком: `payload` на каждой эмиссии пометки не
     * читается и не разбирается (NFR1), и в тексте запроса его нет.
     */
    @Query(
        """
        SELECT id AS recordId, state AS state,
               expected_version AS expectedVersion, server_version AS serverVersion
        FROM outbox
        WHERE owner_uid = :ownerUid AND entity_ref = :entityRef
        ORDER BY (state = 'CONFLICT') DESC, (state = 'QUARANTINED') DESC, created_at_ms DESC
        LIMIT 1
        """,
    )
    fun observeByEntityRef(
        ownerUid: String,
        entityRef: String,
    ): Flow<OutboxEntitySyncRow?>

    /**
     * То же одним снимком и строкой целиком: разрешение конфликта отправляет запись заново, и ему
     * нужны и `payload`, и ключ идемпотентности, а не одни числа.
     */
    @Query(
        """
        SELECT * FROM outbox
        WHERE owner_uid = :ownerUid AND entity_ref = :entityRef
        ORDER BY (state = 'CONFLICT') DESC, (state = 'QUARANTINED') DESC, created_at_ms DESC
        LIMIT 1
        """,
    )
    suspend fun findByEntityRef(
        ownerUid: String,
        entityRef: String,
    ): OutboxEntity?

    /** Отправленное удаляется, а не помечается: очередь — не архив (AD-4). */
    @Query("DELETE FROM outbox WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM outbox WHERE owner_uid = :ownerUid AND state = 'QUARANTINED'")
    suspend fun quarantined(ownerUid: String): List<OutboxEntity>

    @Query(
        "SELECT COUNT(*) FROM outbox WHERE owner_uid = :ownerUid AND state IN ('WAITING', 'WAITING_PRECONDITION')",
    )
    suspend fun countPending(ownerUid: String): Int

    /**
     * Остаток владельца по всем пяти состояниям — для вопроса перед сменой аккаунта (AD-8).
     *
     * Отдельный запрос от [countPending] намеренно: тот отвечает «что поедет» и видит два
     * состояния, а удаляет смена аккаунта все пять. Неизвестное имя состояния (строка из будущей
     * версии) считается карантином — так же, как её читает `RoomOutboxStore`: она не уедет, но и
     * потеряется при переключении, значит про неё надо предупредить.
     */
    @Query(
        """
        SELECT
            SUM(state = 'WAITING') AS waiting,
            SUM(state = 'WAITING_PRECONDITION') AS waitingPrecondition,
            SUM(state = 'CONFLICT') AS conflicted,
            SUM(state NOT IN ('WAITING', 'WAITING_PRECONDITION', 'CONFLICT', 'SYNCHRONOUS')) AS quarantined,
            SUM(state = 'SYNCHRONOUS') AS synchronous
        FROM outbox WHERE owner_uid = :ownerUid
        """,
    )
    suspend fun countRemainder(ownerUid: String): OutboxRemainderRow

    /**
     * Все `uid`, чьи строки лежат в таблице.
     *
     * Спрашивается там, где действующей сессии нет: `auth.currentUser == null` не означает, что
     * терять нечего, — кто прежний владелец, знает очередь, а не аутентификация.
     */
    @Query("SELECT DISTINCT owner_uid FROM outbox")
    suspend fun ownersWithRecords(): List<String>

    /** Числа по каждому состоянию отдельно — одно «ожидает» на все скрыло бы конфликт (AD-14). */
    @Query(
        """
        SELECT
            SUM(state = 'WAITING') AS waiting,
            SUM(state = 'WAITING_PRECONDITION') AS waitingPrecondition,
            SUM(state = 'CONFLICT') AS conflicted,
            SUM(state = 'QUARANTINED') AS quarantined
        FROM outbox WHERE owner_uid = :ownerUid
        """,
    )
    fun observeCounts(ownerUid: String): Flow<OutboxCountsRow>

    /** Записи прежнего владельца после смены аккаунта уезжать не должны (AD-8). */
    @Query("DELETE FROM outbox WHERE owner_uid = :ownerUid")
    suspend fun deleteAllOf(ownerUid: String)
}

/** Строка с остатком владельца. `SUM` по пустой выборке даёт `NULL`, поэтому поля обнуляемые. */
data class OutboxRemainderRow(
    val waiting: Int?,
    val waitingPrecondition: Int?,
    val conflicted: Int?,
    val quarantined: Int?,
    val synchronous: Int?,
)

/**
 * Состояние очереди по одной сущности — ровно те четыре значения, которые нужны пометке.
 *
 * Проекция, а не `OutboxEntity`: строка целиком тащила бы `payload` на каждую эмиссию, а он
 * пометке не нужен ни в каком виде (NFR1, AD-7).
 */
data class OutboxEntitySyncRow(
    val recordId: Long,
    val state: String,
    val expectedVersion: Long?,
    val serverVersion: Long?,
)

/** Строка со сводкой. `SUM` по пустой таблице даёт `NULL`, поэтому поля обнуляемые. */
data class OutboxCountsRow(
    val waiting: Int?,
    val waitingPrecondition: Int?,
    val conflicted: Int?,
    val quarantined: Int?,
)
