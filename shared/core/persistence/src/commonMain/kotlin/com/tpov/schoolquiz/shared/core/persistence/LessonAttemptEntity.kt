package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "lesson_attempts",
    indices = [
        Index(value = ["user_id"], name = "idx_lesson_attempts_user_id"),
        Index(value = ["lesson_id"], name = "idx_lesson_attempts_lesson_id"),
    ],
)
data class LessonAttemptEntity(
    @PrimaryKey
    @ColumnInfo(name = "attempt_id")
    val attemptId: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "lesson_id") val lessonId: String,
    @ColumnInfo(name = "lesson_version") val lessonVersion: Long,
    @ColumnInfo(name = "is_hard") val isHard: Int,
    @ColumnInfo(name = "code_answer") val codeAnswer: String,
    @ColumnInfo(name = "percent_score") val percentScore: Int,
    @ColumnInfo(name = "completed_at") val completedAt: Long,
)
