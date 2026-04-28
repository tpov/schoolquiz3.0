package com.tpov.schoolquiz.shared.feature.lesson_runner.data.mapper

import com.tpov.schoolquiz.shared.core.persistence.LessonRatingSubmittedLocalEntity
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.LessonRating

fun LessonRating.toEntity(): LessonRatingSubmittedLocalEntity = LessonRatingSubmittedLocalEntity(
    userId = userId,
    lessonId = lessonId.value,
    submittedAt = ratedAt,
)
