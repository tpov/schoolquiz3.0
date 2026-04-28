package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake

import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.LessonRating
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonRatingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeLessonRatingRepository : LessonRatingRepository {

    private val submitted = MutableStateFlow<Set<Pair<String, String>>>(emptySet())
    var submitCallCount: Int = 0
        private set
    var submitResult: Result<Unit> = Result.success(Unit)
    val submittedRatings = mutableListOf<LessonRating>()

    override suspend fun submit(rating: LessonRating): Result<Unit> {
        submitCallCount++
        if (submitResult.isSuccess) {
            submitted.value = submitted.value + (rating.userId to rating.lessonId.value)
            submittedRatings += rating
        }
        return submitResult
    }

    override fun hasSubmitted(userId: String, lessonId: LessonId): Flow<Boolean> =
        submitted.map { set -> (userId to lessonId.value) in set }

    fun setAlreadySubmitted(userId: String, lessonId: LessonId) {
        submitted.value = submitted.value + (userId to lessonId.value)
    }
}
