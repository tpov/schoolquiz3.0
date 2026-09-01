package com.tpov.schoolquiz.shared.feature.question.domain.repository

import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.question.domain.model.Question
import com.tpov.schoolquiz.shared.feature.question.domain.model.QuestionId
import kotlinx.coroutines.flow.Flow

/**
 * Domain boundary for reading and syncing [Question]s.
 *
 * Question is the leaf node — no children. Pull uses only the
 * `version > last` filter.
 *
 * Production implementation lives in the data layer (phase-01).
 * Test doubles: [FakeQuestionRepository] in the test source set.
 *
 * Spec: docs/features/home-and-my-quests/0-spec.md — AC#5.
 */
interface QuestionRepository {

    /**
     * Observes questions belonging to [lessonId], sorted by [Question.order] ASC.
     */
    fun observeByLesson(lessonId: LessonId): Flow<List<Question>>

    /**
     * Returns a single question from the local cache, or null if absent.
     */
    suspend fun getById(id: QuestionId): Question?

    /**
     * Pulls concrete questions by id for sync-list based refresh.
     */
    suspend fun refreshByIds(ids: Set<QuestionId>): Result<Unit> = Result.success(Unit)

    /**
     * Pulls questions for the given [lessonIds] from remote and persists locally.
     *
     * Lesson ids come from the `sync_changes` journal. Fetches with
     * `where('lessonId', 'in', batch).where('lastModifiedAt', '>', cursor)`.
     * Batched in groups of <= 30 (Firestore `in`-limit).
     *
     * Cursor must be updated after successful refresh to max(lastModifiedAt).
     *
     * @param lessonIds set of lesson ids whose questions to pull.
     * @param cursor    local questionsCursor (max lastModifiedAt seen so far).
     */
    suspend fun refreshByParents(lessonIds: Set<LessonId>, cursor: Long): Result<Unit>
}
