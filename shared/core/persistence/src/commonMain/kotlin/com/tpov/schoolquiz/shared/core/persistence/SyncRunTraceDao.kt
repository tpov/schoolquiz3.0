package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * След прогона в базе: читается потоком, пишется двумя движениями — успехом и неудачей.
 *
 * Читается сразу по всем видам прогона одной подпиской: экран показывает их рядом, и вторая
 * подписка на ту же таблицу означала бы два несогласованных снимка одного и того же.
 *
 * Обе записи идут транзакцией с чтением прежней строки, потому что каждая меняет свою половину и
 * обязана оставить чужую целой: неудача не отменяет того, что прогон когда-то прошёл, а успех
 * снимает жалобу на то, что уже починилось. Трогается при этом только строка своего вида: удачный
 * профильный прогон не имеет права ни объявить свежим контентный, ни снять его ветвь.
 */
@Dao
interface SyncRunTraceDao {
    @Query("SELECT * FROM sync_run_trace WHERE owner_uid = :ownerUid")
    fun observe(ownerUid: String): Flow<List<SyncRunTraceEntity>>

    @Query("SELECT * FROM sync_run_trace WHERE owner_uid = :ownerUid AND kind = :kind")
    suspend fun find(
        ownerUid: String,
        kind: String,
    ): SyncRunTraceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(entity: SyncRunTraceEntity)

    /** Удачный проход этого вида: время новое, ошибки этого вида больше нет. */
    @Transaction
    suspend fun recordSuccess(
        ownerUid: String,
        kind: String,
        atMs: Long,
    ) {
        insertOrReplace(
            SyncRunTraceEntity(ownerUid = ownerUid, kind = kind, lastSuccessAtMs = atMs),
        )
    }

    /** Неудача этого вида: ветвь новая, время последнего успеха этого же вида прежнее. */
    @Transaction
    suspend fun recordFailure(
        ownerUid: String,
        kind: String,
        branch: String,
        reason: String?,
        serverVersion: Long?,
    ) {
        val current = find(ownerUid, kind)
        insertOrReplace(
            SyncRunTraceEntity(
                ownerUid = ownerUid,
                kind = kind,
                lastSuccessAtMs = current?.lastSuccessAtMs ?: 0L,
                lastErrorBranch = branch,
                lastErrorReason = reason,
                lastErrorServerVersion = serverVersion,
            ),
        )
    }
}
