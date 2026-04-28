package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider

import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.RatingId

interface RatingIdProvider {
    fun provide(userId: String, lessonId: LessonId): RatingId
}
