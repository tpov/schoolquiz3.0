package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogDao {

    @Query("SELECT * FROM catalogs WHERE archived = 0 ORDER BY id ASC")
    fun observeAll(): Flow<List<CatalogEntity>>

    @Query("SELECT * FROM catalogs WHERE id = :id")
    suspend fun findById(id: String): CatalogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(entity: CatalogEntity)

    @Transaction
    suspend fun upsertByIdIfNewerVersion(entity: CatalogEntity) {
        val existing = findById(entity.id)
        if (existing == null || existing.version < entity.version) {
            insertOrReplace(entity)
        }
    }

    @Query("DELETE FROM catalogs WHERE id = :id")
    suspend fun deleteById(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<CatalogEntity>)

    @Query("DELETE FROM catalogs")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(entities: List<CatalogEntity>) {
        deleteAll()
        insertAll(entities)
    }
}
