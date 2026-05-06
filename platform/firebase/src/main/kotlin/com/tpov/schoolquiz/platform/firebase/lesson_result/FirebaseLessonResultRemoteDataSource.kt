package com.tpov.schoolquiz.platform.firebase.lesson_result

import com.google.firebase.functions.FirebaseFunctions
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.LessonResultAttemptEvent
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.LessonResultRemoteDataSource
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.QuestRatingEvent
import kotlinx.coroutines.tasks.await

class FirebaseLessonResultRemoteDataSource(
    private val functions: FirebaseFunctions,
) : LessonResultRemoteDataSource {
    override suspend fun submitAttempts(attempts: List<LessonResultAttemptEvent>) {
        if (attempts.isEmpty()) return
        functions
            .getHttpsCallable(SUBMIT_LESSON_RESULT_EVENTS)
            .call(mapOf(ATTEMPTS to attempts.map { it.toCallableMap() }))
            .await()
    }

    override suspend fun submitRatings(ratings: List<QuestRatingEvent>) {
        if (ratings.isEmpty()) return
        functions
            .getHttpsCallable(SUBMIT_QUEST_RATING_EVENTS)
            .call(mapOf(RATINGS to ratings.map { it.toCallableMap() }))
            .await()
    }

    private fun LessonResultAttemptEvent.toCallableMap(): Map<String, Any?> =
        mapOf(
            ATTEMPT_ID to attemptId,
            USER_ID to userId,
            SCOPE to scope,
            OWNER_UID to ownerUid,
            CATALOG_ID to catalogId,
            QUEST_ID to questId,
            SECTION_ID to sectionId,
            THEME_ID to themeId,
            LESSON_ID to lessonId,
            LESSON_VERSION to lessonVersion,
            SOURCE_SHELF to sourceShelf,
            DIFFICULTY to difficulty,
            CODE_ANSWER to codeAnswer,
            PERCENT_SCORE to percentScore,
            COMPLETED_AT_MS to completedAtMs,
            CREATED_AT_MS to createdAtMs,
        )

    private fun QuestRatingEvent.toCallableMap(): Map<String, Any?> =
        mapOf(
            RATING_ID to ratingId,
            USER_ID to userId,
            SCOPE to scope,
            OWNER_UID to ownerUid,
            CATALOG_ID to catalogId,
            QUEST_ID to questId,
            SECTION_ID to sectionId,
            THEME_ID to themeId,
            LESSON_ID to lessonId,
            LESSON_VERSION to lessonVersion,
            SOURCE_SHELF to sourceShelf,
            RATING to rating,
            RATED_AT_MS to ratedAtMs,
            CREATED_AT_MS to createdAtMs,
        )

    private companion object {
        const val SUBMIT_LESSON_RESULT_EVENTS = "submitLessonResultEvents"
        const val SUBMIT_QUEST_RATING_EVENTS = "submitQuestRatingEvents"
        const val ATTEMPTS = "attempts"
        const val RATINGS = "ratings"
        const val ATTEMPT_ID = "attemptId"
        const val RATING_ID = "ratingId"
        const val USER_ID = "userId"
        const val SCOPE = "scope"
        const val OWNER_UID = "ownerUid"
        const val CATALOG_ID = "catalogId"
        const val QUEST_ID = "questId"
        const val SECTION_ID = "sectionId"
        const val THEME_ID = "themeId"
        const val LESSON_ID = "lessonId"
        const val LESSON_VERSION = "lessonVersion"
        const val SOURCE_SHELF = "sourceShelf"
        const val DIFFICULTY = "difficulty"
        const val CODE_ANSWER = "codeAnswer"
        const val PERCENT_SCORE = "percentScore"
        const val COMPLETED_AT_MS = "completedAtMs"
        const val CREATED_AT_MS = "createdAtMs"
        const val RATING = "rating"
        const val RATED_AT_MS = "ratedAtMs"
    }
}
