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

    // Unsent rows only. Neither table is ever deleted from — a delivered row keeps its place and
    // is marked with sent_at_ms — so counting every row would count a device's whole history and
    // never reach zero. sent_at_ms is written only after the server has accepted the batch, so
    // "still null" is exactly "the server does not have this yet".
    @Query("SELECT COUNT(*) FROM lesson_result_attempt_outbox WHERE sent_at_ms IS NULL")
    suspend fun countUnsentAttempts(): Int

    @Query("SELECT COUNT(*) FROM quest_rating_outbox WHERE sent_at_ms IS NULL")
    suspend fun countUnsentRatings(): Int
}
