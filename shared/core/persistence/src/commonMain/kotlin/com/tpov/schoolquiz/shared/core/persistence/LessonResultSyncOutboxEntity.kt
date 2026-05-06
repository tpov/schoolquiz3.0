package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "lesson_result_attempt_outbox",
    indices = [
        Index(value = ["sent_at_ms", "completed_at_ms"], name = "idx_result_attempt_outbox_pending"),
        Index(value = ["scope", "catalog_id", "quest_id"], name = "idx_result_attempt_outbox_content"),
    ],
)
data class LessonResultAttemptOutboxEntity(
    @PrimaryKey
    @ColumnInfo(name = "attempt_id")
    val attemptId: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "scope") val scope: String,
    @ColumnInfo(name = "owner_uid") val ownerUid: String?,
    @ColumnInfo(name = "catalog_id") val catalogId: String,
    @ColumnInfo(name = "quest_id") val questId: String,
    @ColumnInfo(name = "section_id") val sectionId: String,
    @ColumnInfo(name = "theme_id") val themeId: String,
    @ColumnInfo(name = "lesson_id") val lessonId: String,
    @ColumnInfo(name = "lesson_version") val lessonVersion: Long,
    @ColumnInfo(name = "source_shelf") val sourceShelf: String,
    @ColumnInfo(name = "difficulty") val difficulty: String,
    @ColumnInfo(name = "code_answer") val codeAnswer: String,
    @ColumnInfo(name = "percent_score") val percentScore: Int,
    @ColumnInfo(name = "completed_at_ms") val completedAtMs: Long,
    @ColumnInfo(name = "created_at_ms") val createdAtMs: Long,
    @ColumnInfo(name = "sent_at_ms") val sentAtMs: Long? = null,
    @ColumnInfo(name = "last_error") val lastError: String? = null,
)

@Entity(
    tableName = "quest_rating_outbox",
    indices = [
        Index(value = ["sent_at_ms", "rated_at_ms"], name = "idx_quest_rating_outbox_pending"),
        Index(value = ["scope", "catalog_id", "quest_id"], name = "idx_quest_rating_outbox_content"),
    ],
)
data class QuestRatingOutboxEntity(
    @PrimaryKey
    @ColumnInfo(name = "rating_id")
    val ratingId: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "scope") val scope: String,
    @ColumnInfo(name = "owner_uid") val ownerUid: String?,
    @ColumnInfo(name = "catalog_id") val catalogId: String,
    @ColumnInfo(name = "quest_id") val questId: String,
    @ColumnInfo(name = "section_id") val sectionId: String,
    @ColumnInfo(name = "theme_id") val themeId: String,
    @ColumnInfo(name = "lesson_id") val lessonId: String,
    @ColumnInfo(name = "lesson_version") val lessonVersion: Long,
    @ColumnInfo(name = "source_shelf") val sourceShelf: String,
    @ColumnInfo(name = "rating") val rating: Int,
    @ColumnInfo(name = "rated_at_ms") val ratedAtMs: Long,
    @ColumnInfo(name = "created_at_ms") val createdAtMs: Long,
    @ColumnInfo(name = "sent_at_ms") val sentAtMs: Long? = null,
    @ColumnInfo(name = "last_error") val lastError: String? = null,
)
