package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface QuestDao {

    @Query("""
        SELECT * FROM quests
        WHERE authorUid = :authorUid AND archived = 0
        ORDER BY lastModifiedAt DESC
    """)
    fun observeMyQuests(authorUid: String): Flow<List<QuestEntity>>

    @Query("""
        SELECT * FROM quests
        WHERE authorUid = :authorUid AND catalogId = :catalogId AND archived = 0
        ORDER BY lastModifiedAt DESC
    """)
    fun observeMyQuestsInCatalog(authorUid: String, catalogId: String): Flow<List<QuestEntity>>

    // Delimiter-wrapped exact-element match: prevents "shelf1" matching "shelf10"
    @Query("""
        SELECT * FROM quests
        WHERE (CHAR(31) || visibleOn || CHAR(31)) LIKE ('%' || CHAR(31) || :shelf || CHAR(31) || '%')
        AND archived = 0
        ORDER BY lastModifiedAt DESC
    """)
    fun observeByShelf(shelf: String): Flow<List<QuestEntity>>

    @Query("""
        SELECT * FROM quests
        WHERE catalogId = :catalogId
        AND (CHAR(31) || visibleOn || CHAR(31)) LIKE ('%' || CHAR(31) || :shelf || CHAR(31) || '%')
        AND archived = 0
        ORDER BY lastModifiedAt DESC
    """)
    fun observeByCatalog(catalogId: String, shelf: String): Flow<List<QuestEntity>>

    @Query("SELECT * FROM quests WHERE id = :id")
    suspend fun findById(id: String): QuestEntity?

    @Query("SELECT COUNT(*) FROM catalogs WHERE id = :id")
    suspend fun catalogCount(id: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(entity: QuestEntity)

    @Transaction
    suspend fun upsertByIdIfNewerVersion(entity: QuestEntity) {
        val existing = findById(entity.id)
        if (existing == null || existing.version < entity.version) {
            insertOrReplace(entity)
        }
    }

    @Transaction
    suspend fun upsertFromSyncList(entity: QuestEntity) {
        if (catalogCount(entity.catalogId) == 0) {
            deleteById(entity.id)
            return
        }
        val existing = findById(entity.id)
        if (existing == null || existing.shouldBeReplacedBySyncList(entity)) {
            insertOrReplace(entity)
        }
    }

    @Query("DELETE FROM quests WHERE id = :id")
    suspend fun deleteById(id: String)

    private fun QuestEntity.shouldBeReplacedBySyncList(incoming: QuestEntity): Boolean =
        version < incoming.version ||
            lastModifiedAt < incoming.lastModifiedAt ||
            (lastModifiedAt == incoming.lastModifiedAt && this != incoming)
}
