package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case

import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson.domain.repository.LessonRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.LessonRating
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.RatingId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonRatingRepository
import kotlinx.datetime.Clock

/**
 * Submits a lesson rating from the result screen.
 *
 * [userId] is passed explicitly (from completed attempt snapshot) to avoid auth race.
 * [rating] must be in 1..3.
 * lessonVersion fetched fresh from [lessonRepository] at submit time (for analytics).
 *
 * Returns [Result.failure] on error; local hasSubmitted flag not set on failure.
 */
class SubmitLessonRatingUseCase(
    private val ratingRepository: LessonRatingRepository,
    private val lessonRepository: LessonRepository,
    private val clock: Clock,
    private val ratingIdProvider: (userId: String, lessonId: LessonId) -> RatingId,
) {
    suspend operator fun invoke(
        userId: String,
        lessonId: LessonId,
        rating: Int,
    ): Result<Unit> {
        if (rating !in 1..3) {
            return Result.failure(IllegalArgumentException("rating must be in 1..3, got $rating"))
        }

        val lesson = lessonRepository.getById(lessonId)
            ?: return Result.failure(IllegalStateException("Lesson not found: ${lessonId.value}"))

        val ratingId = ratingIdProvider(userId, lessonId)
        val nowMs = clock.now().toEpochMilliseconds()

        val lessonRating = LessonRating(
            id = ratingId,
            userId = userId,
            lessonId = lessonId,
            lessonVersion = lesson.version,
            rating = rating,
            ratedAt = nowMs,
        )

        return ratingRepository.submit(lessonRating)
    }
}
