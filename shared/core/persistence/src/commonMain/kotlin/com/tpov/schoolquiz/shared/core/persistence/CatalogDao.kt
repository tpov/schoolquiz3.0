package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogDao {

    @Query("SELECT * FROM catalogs WHERE archived = 0 ORDER BY id ASC")
    fun observeAll(): Flow<List<CatalogEntity>>

    @Query("SELECT * FROM catalogs WHERE id = :id")
    suspend fun findById(id: String): CatalogEntity?

    // Upsert, not INSERT OR REPLACE: SQLite implements REPLACE as delete + insert, and
    // deleting this row cascades through quests → sections → themes → lessons → questions.
    // A metadata-only update would wipe the downloaded subtree without restoring it.
    @Upsert
    suspend fun insertOrReplace(entity: CatalogEntity)

    @Transaction
    suspend fun upsertByIdIfNewerVersion(entity: CatalogEntity) {
        val existing = findById(entity.id)
        if (existing == null || existing.shouldBeReplacedBy(entity)) {
            insertOrReplace(entity)
        }
    }

    @Query("DELETE FROM catalogs WHERE id = :id")
    suspend fun deleteById(id: String)

    @Upsert
    suspend fun insertAll(entities: List<CatalogEntity>)

    @Query("DELETE FROM catalogs")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(entities: List<CatalogEntity>) {
        deleteAll()
        insertAll(entities)
    }

    private fun CatalogEntity.shouldBeReplacedBy(incoming: CatalogEntity): Boolean =
        version < incoming.version ||
            (version == incoming.version && lastModifiedAt < incoming.lastModifiedAt) ||
            (version == incoming.version && lastModifiedAt == incoming.lastModifiedAt && this != incoming)
}
