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

    @Query(
        """
        UPDATE outbox
        SET state = :state,
            next_retry_at_ms = :nextRetryAtMs,
            attempt_count = :attemptCount,
            last_error = :lastError
        WHERE id = :id
        """,
    )
    suspend fun applyDecision(
        id: Long,
        state: String,
        nextRetryAtMs: Long,
        attemptCount: Int,
        lastError: String?,
    )

    /** Отправленное удаляется, а не помечается: очередь — не архив (AD-4). */
    @Query("DELETE FROM outbox WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM outbox WHERE owner_uid = :ownerUid AND state = 'QUARANTINED'")
    suspend fun quarantined(ownerUid: String): List<OutboxEntity>

    @Query(
        "SELECT COUNT(*) FROM outbox WHERE owner_uid = :ownerUid AND state IN ('WAITING', 'WAITING_PRECONDITION')",
    )
    suspend fun countPending(ownerUid: String): Int

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

/** Строка со сводкой. `SUM` по пустой таблице даёт `NULL`, поэтому поля обнуляемые. */
data class OutboxCountsRow(
    val waiting: Int?,
    val waitingPrecondition: Int?,
    val conflicted: Int?,
    val quarantined: Int?,
)
