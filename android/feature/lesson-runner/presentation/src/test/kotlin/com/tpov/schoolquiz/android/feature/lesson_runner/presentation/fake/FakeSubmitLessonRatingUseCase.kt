package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake

import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId

class FakeSubmitLessonRatingUseCase {
    var result: Result<Unit> = Result.success(Unit)
    var callCount = 0
    var lastRating: Int? = null
    var lastUserId: String? = null
    var lastLessonId: LessonId? = null
    var lastAttemptId: AttemptId? = null

    suspend operator fun invoke(userId: String, lessonId: LessonId, attemptId: AttemptId, rating: Int): Result<Unit> {
        callCount++
        lastUserId = userId
        lastLessonId = lessonId
        lastAttemptId = attemptId
        lastRating = rating
        return result
    }
}
