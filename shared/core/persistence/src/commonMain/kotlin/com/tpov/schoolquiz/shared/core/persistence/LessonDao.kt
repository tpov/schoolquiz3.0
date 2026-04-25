package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface LessonDao {

    @Query("SELECT * FROM lessons WHERE themeId = :themeId AND archived = 0 ORDER BY `order` ASC")
    fun observeByTheme(themeId: String): Flow<List<LessonEntity>>

    @Query("SELECT * FROM lessons WHERE id = :id")
    suspend fun findById(id: String): LessonEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(entity: LessonEntity)

    @Transaction
    suspend fun upsertByIdIfNewerVersion(entity: LessonEntity) {
        val existing = findById(entity.id)
        if (existing == null || existing.version < entity.version) {
            insertOrReplace(entity)
        }
    }

    @Query("DELETE FROM lessons WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT contentsVersion FROM lessons WHERE id = :id")
    suspend fun getContentsVersion(id: String): Long?
}
