package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake

import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId

class FakeSubmitLessonRatingUseCase {
    var result: Result<Unit> = Result.success(Unit)
    var callCount = 0
    var lastRating: Int? = null
    var lastUserId: String? = null
    var lastLessonId: LessonId? = null

    suspend operator fun invoke(userId: String, lessonId: LessonId, rating: Int): Result<Unit> {
        callCount++
        lastUserId = userId
        lastLessonId = lessonId
        lastRating = rating
        return result
    }
}
