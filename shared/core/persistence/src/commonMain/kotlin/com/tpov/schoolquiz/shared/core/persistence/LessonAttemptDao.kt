package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface LessonAttemptDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LessonAttemptEntity): Long

    @Upsert
    suspend fun upsertAnswers(entities: List<QuestionAnswerEntity>)

    @Upsert
    suspend fun upsertRepetitions(entities: List<QuestionRepetitionEntity>)

    @Upsert
    suspend fun upsertOutboxRow(entity: LessonResultAttemptOutboxEntity)

    /**
     * Stores a finished attempt: the attempt itself, the answers behind it, the repetition
     * schedule those answers move, and the row that will carry it all to the server.
     *
     * One transaction on purpose. Two failure modes used to be possible and both were silent:
     * a scored attempt with no answers would skew every statistic derived from them, and an
     * attempt saved but never queued would stay on the device forever while the UI reported a
     * save failure.
     */
    @Transaction
    suspend fun saveAttemptWithAnswers(
        attempt: LessonAttemptEntity,
        answers: List<QuestionAnswerEntity>,
        repetitions: List<QuestionRepetitionEntity>,
        outboxRow: LessonResultAttemptOutboxEntity? = null,
    ) {
        upsert(attempt)
        if (answers.isNotEmpty()) upsertAnswers(answers)
        if (repetitions.isNotEmpty()) upsertRepetitions(repetitions)
        outboxRow?.let { upsertOutboxRow(it) }
    }

    @Query("SELECT * FROM lesson_attempts WHERE user_id = :userId AND lesson_id = :lessonId")
    fun observeByLesson(userId: String, lessonId: String): Flow<List<LessonAttemptEntity>>

    @Query("SELECT * FROM lesson_attempts WHERE user_id = :userId")
    fun observeAllByUser(userId: String): Flow<List<LessonAttemptEntity>>
}
