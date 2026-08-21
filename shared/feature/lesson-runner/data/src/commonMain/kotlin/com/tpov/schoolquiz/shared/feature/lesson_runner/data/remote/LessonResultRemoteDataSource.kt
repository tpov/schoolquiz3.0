package com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote

data class LessonResultAttemptEvent(
    val attemptId: String,
    val userId: String,
    val scope: String,
    val ownerUid: String?,
    val catalogId: String,
    val questId: String,
    val sectionId: String,
    val themeId: String,
    val lessonId: String,
    val lessonVersion: Long,
    val sourceShelf: String,
    val difficulty: String,
    val codeAnswer: String,
    val percentScore: Int,
    val completedAtMs: Long,
    val createdAtMs: Long,
    /**
     * The answers this attempt is made of. Sent with the attempt rather than through a queue
     * of their own: they are written in the same transaction, so they can never disagree.
     */
    val answers: List<LessonAnswerEvent> = emptyList(),
)

data class LessonAnswerEvent(
    val questionId: String,
    val codeAnswerIndex: Int,
    val score: Int,
    val answerPayload: String,
    val answeredAtMs: Long,
    val durationMs: Long,
    val wasTimeout: Boolean,
)

data class QuestRatingEvent(
    val ratingId: String,
    val userId: String,
    val scope: String,
    val ownerUid: String?,
    val catalogId: String,
    val questId: String,
    val sectionId: String,
    val themeId: String,
    val lessonId: String,
    val lessonVersion: Long,
    val sourceShelf: String,
    val rating: Int,
    val ratedAtMs: Long,
    val createdAtMs: Long,
)

interface LessonResultRemoteDataSource {
    suspend fun submitAttempts(attempts: List<LessonResultAttemptEvent>)

    suspend fun submitRatings(ratings: List<QuestRatingEvent>)
}
