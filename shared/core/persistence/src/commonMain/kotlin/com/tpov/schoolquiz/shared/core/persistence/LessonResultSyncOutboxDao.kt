package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LessonResultSyncOutboxDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAttempt(entity: LessonResultAttemptOutboxEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRating(entity: QuestRatingOutboxEntity)

    @Query(
        """
        SELECT * FROM lesson_result_attempt_outbox
        WHERE sent_at_ms IS NULL
        ORDER BY completed_at_ms ASC
        LIMIT :limit
        """,
    )
    suspend fun pendingAttempts(limit: Int): List<LessonResultAttemptOutboxEntity>

    @Query(
        """
        SELECT * FROM quest_rating_outbox
        WHERE sent_at_ms IS NULL
        ORDER BY rated_at_ms ASC
        LIMIT :limit
        """,
    )
    suspend fun pendingRatings(limit: Int): List<QuestRatingOutboxEntity>

    @Query(
        """
        UPDATE lesson_result_attempt_outbox
        SET sent_at_ms = :sentAtMs, last_error = NULL
        WHERE attempt_id IN (:ids)
        """,
    )
    suspend fun markAttemptsSent(ids: List<String>, sentAtMs: Long)

    @Query(
        """
        UPDATE quest_rating_outbox
        SET sent_at_ms = :sentAtMs, last_error = NULL
        WHERE rating_id IN (:ids)
        """,
    )
    suspend fun markRatingsSent(ids: List<String>, sentAtMs: Long)

    @Query(
        """
        UPDATE lesson_result_attempt_outbox
        SET last_error = :error
        WHERE attempt_id IN (:ids)
        """,
    )
    suspend fun markAttemptsFailed(ids: List<String>, error: String)

    @Query(
        """
        UPDATE quest_rating_outbox
        SET last_error = :error
        WHERE rating_id IN (:ids)
        """,
    )
    suspend fun markRatingsFailed(ids: List<String>, error: String)
}
