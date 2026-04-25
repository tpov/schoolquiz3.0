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

    @Query("SELECT * FROM quests WHERE id = :id")
    suspend fun findById(id: String): QuestEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(entity: QuestEntity)

    @Transaction
    suspend fun upsertByIdIfNewerVersion(entity: QuestEntity) {
        val existing = findById(entity.id)
        if (existing == null || existing.version < entity.version) {
            insertOrReplace(entity)
        }
    }

    @Query("DELETE FROM quests WHERE id = :id")
    suspend fun deleteById(id: String)
}
