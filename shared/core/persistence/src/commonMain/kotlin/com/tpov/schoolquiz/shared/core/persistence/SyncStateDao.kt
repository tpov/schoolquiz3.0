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

    @Transaction
    suspend fun setCursor(collectionId: String, value: Long) {
        val current = getCursor(collectionId) ?: 0L
        insertOrReplace(SyncStateEntity(collectionId, maxOf(current, value)))
    }
}
