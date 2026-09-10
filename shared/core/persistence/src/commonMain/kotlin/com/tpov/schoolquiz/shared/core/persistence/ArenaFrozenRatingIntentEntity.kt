package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * The first arena-rating request, retained independently of the shared lesson marker.
 *
 * A marker only says that a user rated a lesson.  It cannot recover the random rating id or the
 * exact frozen body after a lost reply, so it is deliberately not used as this intent's source.
 */
@Entity(
    tableName = "arena_frozen_rating_intents",
    primaryKeys = ["owner_uid", "ticket_id"],
    indices = [Index(value = ["owner_uid", "mutation_id"], unique = true), Index(value = ["owner_uid", "lesson_id"])],
)
data class ArenaFrozenRatingIntentEntity(
    @ColumnInfo(name = "owner_uid") val ownerUid: String,
    @ColumnInfo(name = "ticket_id") val ticketId: String,
    @ColumnInfo(name = "attempt_id") val attemptId: String,
    @ColumnInfo(name = "lesson_id") val lessonId: String,
    @ColumnInfo(name = "rating_id") val ratingId: String,
    @ColumnInfo(name = "mutation_id") val mutationId: String,
    @ColumnInfo(name = "operation") val operation: String,
    @ColumnInfo(name = "payload") val payload: String,
    @ColumnInfo(name = "entity_ref") val entityRef: String,
    @ColumnInfo(name = "created_at_ms") val createdAtMs: Long,
)
