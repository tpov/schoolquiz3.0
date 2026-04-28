package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model

import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId

/**
 * Immutable record of a completed or aborted lesson attempt.
 * One write per attempt (Room); not edited after save.
 */
data class Attempt(
    val id: AttemptId,
    val userId: String,
    val lessonId: LessonId,
    val lessonVersion: Long,
    val mode: Difficulty,
    val completedAt: Long,
    val codeAnswer: CodeAnswer,
    val percentScore: PercentScore,
) {
    init {
        require(userId.isNotBlank()) { "Attempt.userId must not be blank" }
        require(lessonVersion >= 1) { "Attempt.lessonVersion must be >= 1, got $lessonVersion" }
        require(completedAt >= 0) { "Attempt.completedAt must be >= 0, got $completedAt" }
    }
}
