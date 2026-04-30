package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface QuestionDao {

    @Query("SELECT * FROM questions WHERE lessonId = :lessonId AND archived = 0 ORDER BY `order` ASC")
    fun observeByLesson(lessonId: String): Flow<List<QuestionEntity>>

    @Query("SELECT * FROM questions WHERE id = :id")
    suspend fun findById(id: String): QuestionEntity?

    @Query("SELECT COUNT(*) FROM lessons WHERE id = :id")
    suspend fun lessonCount(id: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(entity: QuestionEntity)

    @Transaction
    suspend fun upsertByIdIfNewerVersion(entity: QuestionEntity) {
        val existing = findById(entity.id)
        if (existing == null || existing.version < entity.version) {
            insertOrReplace(entity)
        }
    }

    @Transaction
    suspend fun upsertFromSyncList(entity: QuestionEntity) {
        if (lessonCount(entity.lessonId) == 0) {
            deleteById(entity.id)
            return
        }
        val existing = findById(entity.id)
        if (existing == null || existing.shouldBeReplacedBySyncList(entity)) {
            insertOrReplace(entity)
        }
    }

    @Query("DELETE FROM questions WHERE id = :id")
    suspend fun deleteById(id: String)

    private fun QuestionEntity.shouldBeReplacedBySyncList(incoming: QuestionEntity): Boolean =
        version < incoming.version ||
            lastModifiedAt < incoming.lastModifiedAt ||
            (lastModifiedAt == incoming.lastModifiedAt && this != incoming)
}
