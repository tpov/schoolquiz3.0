package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository

import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.LessonRating
import kotlinx.coroutines.flow.Flow

interface LessonRatingRepository {

    /**
     * Submits [rating] to Room local table and queues for cascade sync.
     * Returns [Result.failure] on error; local hasSubmitted flag NOT set on failure.
     */
    suspend fun submit(rating: LessonRating): Result<Unit>

    /**
     * Observes whether [userId] has already submitted a rating for [lessonId].
     * Backed by Room compound PK (userId, lessonId) table.
     */
    fun hasSubmitted(userId: String, lessonId: LessonId): Flow<Boolean>
}
