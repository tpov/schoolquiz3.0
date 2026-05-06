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
