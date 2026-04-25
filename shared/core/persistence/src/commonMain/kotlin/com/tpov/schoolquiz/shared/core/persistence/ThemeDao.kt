package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ThemeDao {

    @Query("SELECT * FROM themes WHERE sectionId = :sectionId AND archived = 0 ORDER BY `order` ASC")
    fun observeBySection(sectionId: String): Flow<List<ThemeEntity>>

    @Query("SELECT * FROM themes WHERE id = :id")
    suspend fun findById(id: String): ThemeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(entity: ThemeEntity)

    @Transaction
    suspend fun upsertByIdIfNewerVersion(entity: ThemeEntity) {
        val existing = findById(entity.id)
        if (existing == null || existing.version < entity.version) {
            insertOrReplace(entity)
        }
    }

    @Query("DELETE FROM themes WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT contentsVersion FROM themes WHERE id = :id")
    suspend fun getContentsVersion(id: String): Long?
}
