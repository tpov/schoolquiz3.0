package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LessonRatingLocalDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LessonRatingSubmittedLocalEntity): Long

    @Query("SELECT COUNT(*) > 0 FROM lesson_rating_submitted_local WHERE user_id = :userId AND lesson_id = :lessonId")
    fun hasSubmitted(userId: String, lessonId: String): Flow<Boolean>
}
