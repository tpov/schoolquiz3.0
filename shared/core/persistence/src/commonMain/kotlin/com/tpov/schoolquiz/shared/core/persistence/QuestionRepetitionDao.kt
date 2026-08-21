package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface QuestionRepetitionDao {

    @Upsert
    suspend fun upsertAll(entities: List<QuestionRepetitionEntity>)

    @Query("SELECT * FROM question_repetition WHERE user_id = :userId AND question_id = :questionId")
    suspend fun findByQuestion(userId: String, questionId: String): QuestionRepetitionEntity?

    @Query(
        """
        SELECT * FROM question_repetition
        WHERE user_id = :userId AND question_id IN (:questionIds)
        """,
    )
    suspend fun findByQuestions(userId: String, questionIds: List<String>): List<QuestionRepetitionEntity>

    /** Questions whose next showing is due — the repetition queue. */
    @Query(
        """
        SELECT * FROM question_repetition
        WHERE user_id = :userId AND next_review_at_ms <= :nowMs
        ORDER BY next_review_at_ms ASC
        LIMIT :limit
        """,
    )
    suspend fun dueForReview(userId: String, nowMs: Long, limit: Int): List<QuestionRepetitionEntity>

    @Query("SELECT COUNT(*) FROM question_repetition WHERE user_id = :userId AND next_review_at_ms <= :nowMs")
    fun observeDueCount(userId: String, nowMs: Long): Flow<Int>
}
