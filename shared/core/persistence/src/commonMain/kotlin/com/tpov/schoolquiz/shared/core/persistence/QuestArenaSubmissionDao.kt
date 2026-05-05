package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface QuestArenaSubmissionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: QuestArenaSubmissionEntity)

    @Query(
        """
        SELECT * FROM quest_arena_submission_outbox
        ORDER BY requestedAtMs ASC
        LIMIT :limit
        """,
    )
    suspend fun findPending(limit: Int): List<QuestArenaSubmissionEntity>

    @Query(
        """
        UPDATE quest_arena_submission_outbox
        SET attemptCount = attemptCount + 1,
            lastError = :message
        WHERE id = :submissionId
        """,
    )
    suspend fun markFailure(
        submissionId: String,
        message: String?,
    )

    @Query("DELETE FROM quest_arena_submission_outbox WHERE id = :submissionId")
    suspend fun delete(submissionId: String)

    @Transaction
    suspend fun queueSubmission(entity: QuestArenaSubmissionEntity) {
        insert(entity)
    }

    @Transaction
    suspend fun markSent(submissionId: String) {
        delete(submissionId)
    }
}
