package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface SyncStateDao {
    @Query("SELECT cursor FROM sync_state WHERE collectionId = :collectionId")
    suspend fun getCursor(collectionId: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(entity: SyncStateEntity)

    /**
     * Обнуляет все курсоры чтения (AD-30).
     *
     * Удаление, а не запись нулей: отсутствующий курсор и так читается как ноль, а строка с нулём
     * пережила бы смысл — «здесь когда-то что-то было». Очередь отправки этим не затрагивается:
     * ресинк лечит только сторону чтения.
     */
    @Query("DELETE FROM sync_state")
    suspend fun clearAllCursors()

    @Transaction
    suspend fun setCursor(collectionId: String, value: Long) {
        val current = getCursor(collectionId) ?: 0L
        insertOrReplace(SyncStateEntity(collectionId, maxOf(current, value)))
    }
}
