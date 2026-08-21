package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface QuestionAnswerDao {

    @Upsert
    suspend fun upsertAll(entities: List<QuestionAnswerEntity>)

    @Query("SELECT * FROM question_answers WHERE attempt_id = :attemptId ORDER BY code_answer_index ASC")
    suspend fun findByAttempt(attemptId: String): List<QuestionAnswerEntity>

    @Query(
        """
        SELECT * FROM question_answers
        WHERE user_id = :userId AND question_id = :questionId
        ORDER BY answered_at_ms DESC
        """,
    )
    suspend fun findByQuestion(userId: String, questionId: String): List<QuestionAnswerEntity>

    @Query("SELECT * FROM question_answers WHERE lesson_id = :lessonId")
    fun observeByLesson(lessonId: String): Flow<List<QuestionAnswerEntity>>

    @Query("SELECT COUNT(*) FROM question_answers WHERE user_id = :userId")
    suspend fun countForUser(userId: String): Int
}
