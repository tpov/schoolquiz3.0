package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model

import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId

/** A started attempt's immutable identity, independent of whether any score is known. */
data class AttemptIdentity(
    val id: AttemptId,
    val userId: String,
    val lessonId: LessonId,
    val lessonVersion: Long,
    val mode: Difficulty,
) {
    init {
        require(userId.isNotBlank())
        require(lessonVersion >= 0L)
    }
}

fun Attempt.identity(): AttemptIdentity = AttemptIdentity(id, userId, lessonId, lessonVersion, mode)
