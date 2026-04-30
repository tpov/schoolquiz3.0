package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface SectionDao {

    @Query("""
        SELECT * FROM sections
        WHERE questId = :questId AND archived = 0
        ORDER BY `order` ASC
    """)
    fun observeByQuest(questId: String): Flow<List<SectionEntity>>

    @Query("SELECT * FROM sections WHERE id = :id")
    suspend fun findById(id: String): SectionEntity?

    @Query("SELECT COUNT(*) FROM quests WHERE id = :id")
    suspend fun questCount(id: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(entity: SectionEntity)

    @Transaction
    suspend fun upsertByIdIfNewerVersion(entity: SectionEntity) {
        val existing = findById(entity.id)
        if (existing == null || existing.version < entity.version) {
            insertOrReplace(entity)
        }
    }

    @Transaction
    suspend fun upsertFromSyncList(entity: SectionEntity) {
        if (questCount(entity.questId) == 0) {
            deleteById(entity.id)
            return
        }
        val existing = findById(entity.id)
        if (existing == null || existing.shouldBeReplacedBySyncList(entity)) {
            insertOrReplace(entity)
        }
    }

    @Query("DELETE FROM sections WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT contentsVersion FROM sections WHERE id = :id")
    suspend fun getContentsVersion(id: String): Long?

    private fun SectionEntity.shouldBeReplacedBySyncList(incoming: SectionEntity): Boolean =
        version < incoming.version ||
            lastModifiedAt < incoming.lastModifiedAt ||
            (lastModifiedAt == incoming.lastModifiedAt && this != incoming)
}
