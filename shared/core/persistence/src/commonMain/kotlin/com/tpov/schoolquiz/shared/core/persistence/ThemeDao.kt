package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ThemeDao {

    @Query("SELECT * FROM themes WHERE sectionId = :sectionId AND archived = 0 ORDER BY `order` ASC")
    fun observeBySection(sectionId: String): Flow<List<ThemeEntity>>

    @Query("SELECT * FROM themes WHERE id = :id")
    suspend fun findById(id: String): ThemeEntity?

    @Query("SELECT COUNT(*) FROM sections WHERE id = :id")
    suspend fun sectionCount(id: String): Int

    // Upsert, not INSERT OR REPLACE: SQLite implements REPLACE as delete + insert, and
    // deleting this row cascades through lessons → questions.
    // A metadata-only update would wipe the downloaded subtree without restoring it.
    @Upsert
    suspend fun insertOrReplace(entity: ThemeEntity)

    @Transaction
    suspend fun upsertByIdIfNewerVersion(entity: ThemeEntity) {
        val existing = findById(entity.id)
        if (existing == null || existing.version < entity.version) {
            insertOrReplace(entity)
        }
    }

    @Transaction
    suspend fun upsertFromSyncList(entity: ThemeEntity) {
        if (sectionCount(entity.sectionId) == 0) {
            deleteById(entity.id)
            return
        }
        val existing = findById(entity.id)
        if (existing == null || existing.shouldBeReplacedBySyncList(entity)) {
            insertOrReplace(entity)
        }
    }

    @Query("DELETE FROM themes WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT contentsVersion FROM themes WHERE id = :id")
    suspend fun getContentsVersion(id: String): Long?

    private fun ThemeEntity.shouldBeReplacedBySyncList(incoming: ThemeEntity): Boolean =
        version < incoming.version ||
            lastModifiedAt < incoming.lastModifiedAt ||
            (lastModifiedAt == incoming.lastModifiedAt && this != incoming)
}
