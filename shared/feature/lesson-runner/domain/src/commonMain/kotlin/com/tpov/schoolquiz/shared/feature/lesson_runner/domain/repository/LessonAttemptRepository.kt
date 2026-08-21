package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository

import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computeBestStars
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computeHardUnlocked
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AnsweredQuestion
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Derived per-lesson progress used by lesson-list consumers (ADR-QS-16). */
data class LessonAttemptStats(
    val bestStarsRawTenths: Int,
    val hardUnlocked: Boolean,
)

interface LessonAttemptRepository {

    /**
     * Persists [attempt] to Room. Called once at attempt completion or abort.
     * Returns [Result.failure] on IO error; caller transitions to SaveFailed state.
     */
    suspend fun save(attempt: Attempt): Result<Unit>

    /**
     * Persists [attempt] together with the [answers] it is made of, in one transaction.
     *
     * The attempt alone keeps a digit per question; the answers carry what was actually chosen,
     * how long it took and whether the timer stepped in — the data spaced repetition, lesson
     * statistics and survey distributions are built from.
     *
     * Defaults to [save] so fakes that only care about attempts keep working.
     */
    suspend fun save(attempt: Attempt, answers: List<AnsweredQuestion>): Result<Unit> = save(attempt)

    /**
     * Observes all attempts for [userId] + [lessonId], sorted by completedAt DESC.
     */
    fun observeByLesson(userId: String, lessonId: LessonId): Flow<List<Attempt>>

    /**
     * Observes all attempts for [userId] across all lessons.
     */
    fun observeAllByUser(userId: String): Flow<List<Attempt>>

    /**
     * Derived view: maps all user attempts to per-lessonId stats (bestStars + hardUnlocked).
     * Default implementation delegates to [observeAllByUser]; override for performance if needed.
     */
    fun observeAllStatsByUser(userId: String): Flow<Map<LessonId, LessonAttemptStats>> =
        observeAllByUser(userId).map { attempts ->
            attempts.groupBy { it.lessonId }.mapValues { (_, lessonAttempts) ->
                LessonAttemptStats(
                    bestStarsRawTenths = computeBestStars(lessonAttempts).rawTenths,
                    hardUnlocked = computeHardUnlocked(lessonAttempts),
                )
            }
        }
}
