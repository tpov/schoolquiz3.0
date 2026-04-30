package com.tpov.schoolquiz.shared.feature.lesson.domain.repository

import com.tpov.schoolquiz.shared.feature.lesson.domain.model.Lesson
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.theme.domain.model.ThemeId
import kotlinx.coroutines.flow.Flow

/**
 * Domain boundary for reading and syncing [Lesson]s.
 *
 * Production implementation lives in the data layer (phase-01).
 * Test doubles: [FakeLessonRepository] in the test source set.
 *
 * Spec: docs/features/home-and-my-quests/0-spec.md — AC#5.
 */
interface LessonRepository {

    /**
     * Observes lessons belonging to [themeId], sorted by [Lesson.order] ASC.
     */
    fun observeByTheme(themeId: ThemeId): Flow<List<Lesson>>

    /**
     * Returns a single lesson from the local cache, or null if absent.
     */
    suspend fun getById(id: LessonId): Lesson?

    /**
     * Pulls concrete lessons by id for sync-list based refresh.
     */
    suspend fun refreshByIds(ids: Set<LessonId>): Result<Unit> = Result.success(Unit)

    /**
     * Pulls lessons for the given [themeIds] from remote and persists locally.
     *
     * Implements cascading sync step 5: fetch with
     * `where('themeId', 'in', batch).where('lastModifiedAt', '>', cursor)`.
     * Cursor must be updated after successful refresh to max(lastModifiedAt).
     *
     * Spec: FR#14, Business Rule #8.
     *
     * @param themeIds set of theme ids whose lessons to pull.
     * @param cursor   local lessonsCursor (max lastModifiedAt seen so far).
     * @return [Result.success] containing the set of processed [LessonId]s (passed to question sync
     *   as parentIds); [Result.failure] on network/permission errors.
     */
    suspend fun refreshByParents(themeIds: Set<ThemeId>, cursor: Long): Result<Set<LessonId>>

    /**
     * Returns the local [contentsVersion] for [id], or null if the lesson is absent.
     * Used by the cascade orchestrator to detect contentsVersion changes.
     */
    suspend fun getLocalContentsVersion(id: LessonId): Long?
}
