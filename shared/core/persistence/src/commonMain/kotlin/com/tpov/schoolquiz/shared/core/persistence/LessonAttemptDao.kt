package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LessonAttemptDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LessonAttemptEntity): Long

    @Query("SELECT * FROM lesson_attempts WHERE user_id = :userId AND lesson_id = :lessonId")
    fun observeByLesson(userId: String, lessonId: String): Flow<List<LessonAttemptEntity>>

    @Query("SELECT * FROM lesson_attempts WHERE user_id = :userId")
    fun observeAllByUser(userId: String): Flow<List<LessonAttemptEntity>>
}
