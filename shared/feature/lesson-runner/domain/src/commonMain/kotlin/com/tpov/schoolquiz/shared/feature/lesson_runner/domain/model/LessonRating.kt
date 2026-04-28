package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model

import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId

/**
 * Immutable user rating for a lesson. One per (userId, lessonId) lifetime.
 * Remote ID = deterministic sha256("$userId:$lessonId").
 * lessonVersion stored for analytics; not part of uniqueness key.
 */
data class LessonRating(
    val id: RatingId,
    val userId: String,
    val lessonId: LessonId,
    val lessonVersion: Long,
    val rating: Int,
    val ratedAt: Long,
) {
    init {
        require(userId.isNotBlank()) { "LessonRating.userId must not be blank" }
        require(lessonVersion >= 1) { "LessonRating.lessonVersion must be >= 1, got $lessonVersion" }
        require(rating in 1..3) { "LessonRating.rating must be in 1..3, got $rating" }
        require(ratedAt >= 0) { "LessonRating.ratedAt must be >= 0, got $ratedAt" }
    }
}
