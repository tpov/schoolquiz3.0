package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "quest_arena_submission_outbox",
    indices = [
        Index(value = ["draftId"]),
        Index(value = ["requestedAtMs"]),
    ],
)
data class QuestArenaSubmissionEntity(
    @PrimaryKey val id: String,
    val draftId: String,
    val ownerUid: String,
    val localRevision: Long,
    val requestedAtMs: Long,
    val attemptCount: Int,
    val lastError: String?,
)
