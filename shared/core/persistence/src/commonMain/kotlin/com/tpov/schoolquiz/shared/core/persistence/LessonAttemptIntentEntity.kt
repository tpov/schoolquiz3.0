package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** First local terminal request, not a server receipt or proof that an attempt was completed. */
@Entity(
    tableName = "lesson_attempt_intents",
    indices = [Index(value = ["owner_uid"]), Index(value = ["mutation_id"], unique = true)],
)
data class LessonAttemptIntentEntity(
    @PrimaryKey @ColumnInfo(name = "attempt_id") val attemptId: String,
    @ColumnInfo(name = "owner_uid") val ownerUid: String,
    @ColumnInfo(name = "mutation_id") val mutationId: String,
    @ColumnInfo(name = "operation") val operation: String,
    @ColumnInfo(name = "payload") val payload: String,
    @ColumnInfo(name = "entity_ref") val entityRef: String?,
    @ColumnInfo(name = "expected_version") val expectedVersion: Long?,
)

/** A second terminal save may not rewrite the first request, even after its queue row is gone. */
class AttemptIntentConflict : IllegalStateException("The attempt already has a different or unproven terminal intent")

internal fun OutboxEntity.attemptIntent(attemptId: String) =
    LessonAttemptIntentEntity(attemptId, ownerUid, mutationId, operation, payload, entityRef, expectedVersion)
