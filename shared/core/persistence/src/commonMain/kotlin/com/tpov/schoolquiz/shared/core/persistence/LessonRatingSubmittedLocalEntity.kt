package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "lesson_rating_submitted_local",
    primaryKeys = ["user_id", "lesson_id"],
)
data class LessonRatingSubmittedLocalEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "lesson_id") val lessonId: String,
    @ColumnInfo(name = "submitted_at") val submittedAt: Long,
)
