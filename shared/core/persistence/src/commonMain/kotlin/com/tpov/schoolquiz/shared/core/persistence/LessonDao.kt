package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface LessonDao {

    @Query("SELECT * FROM lessons WHERE themeId = :themeId AND archived = 0 ORDER BY `order` ASC")
    fun observeByTheme(themeId: String): Flow<List<LessonEntity>>

    @Query("SELECT * FROM lessons WHERE id = :id")
    suspend fun findById(id: String): LessonEntity?

    @Query("SELECT COUNT(*) FROM themes WHERE id = :id")
    suspend fun themeCount(id: String): Int

    // Upsert, not INSERT OR REPLACE: SQLite implements REPLACE as delete + insert, and
    // deleting this row cascades through questions.
    // A metadata-only update would wipe the downloaded subtree without restoring it.
    @Upsert
    suspend fun insertOrReplace(entity: LessonEntity)

    @Transaction
    suspend fun upsertByIdIfNewerVersion(entity: LessonEntity) {
        val existing = findById(entity.id)
        if (existing == null || existing.version < entity.version) {
            insertOrReplace(entity)
        }
    }

    @Transaction
    suspend fun upsertFromSyncList(entity: LessonEntity) {
        if (themeCount(entity.themeId) == 0) {
            deleteById(entity.id)
            return
        }
        val existing = findById(entity.id)
        if (existing == null || existing.shouldBeReplacedBySyncList(entity)) {
            insertOrReplace(entity)
        }
    }

    @Query("DELETE FROM lessons WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT contentsVersion FROM lessons WHERE id = :id")
    suspend fun getContentsVersion(id: String): Long?

    private fun LessonEntity.shouldBeReplacedBySyncList(incoming: LessonEntity): Boolean =
        version < incoming.version ||
            lastModifiedAt < incoming.lastModifiedAt ||
            (lastModifiedAt == incoming.lastModifiedAt && this != incoming)
}
