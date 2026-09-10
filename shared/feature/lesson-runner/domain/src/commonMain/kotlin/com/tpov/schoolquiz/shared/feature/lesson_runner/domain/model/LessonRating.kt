package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model

import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId

/**
 * Immutable user rating attempt for a lesson. The local marker permits one active vote per
 * (userId, lessonId); a rejected/quarantined vote may be retried with a fresh random ID.
 * lessonVersion is stored for analytics; it is not part of the marker key.
 */
data class LessonRating(
    val id: RatingId,
    val userId: String,
    val lessonId: LessonId,
    val lessonVersion: Long,
    val rating: Int,
    val ratedAt: Long,
    /** Completion proof for the new queued payload; null only for historical/fake callers. */
    val attemptId: AttemptId? = null,
) {
    init {
        require(userId.isNotBlank()) { "LessonRating.userId must not be blank" }
        require(lessonVersion >= 1) { "LessonRating.lessonVersion must be >= 1, got $lessonVersion" }
        require(rating in 1..3) { "LessonRating.rating must be in 1..3, got $rating" }
        require(ratedAt >= 0) { "LessonRating.ratedAt must be >= 0, got $ratedAt" }
    }
}
