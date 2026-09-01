package com.tpov.schoolquiz.shared.feature.question.domain.use_case

import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.question.domain.repository.QuestionRepository

/**
 * Triggers remote sync for questions of the given lessons.
 *
 * Leaf-level pull: the lesson ids to refresh come from the `sync_changes`
 * journal; there is no parent-version cascade.
 *
 * Spec: docs/features/home-and-my-quests/0-spec.md — FR#14.
 * Primary User Journey 5: admin adds question → question reaches the client.
 */
class SyncQuestionsUseCase(
    private val questions: QuestionRepository,
) {
    /**
     * @param lessonIds IDs of lessons whose questions need to be refreshed.
     * @param cursor    local questionsCursor (max lastModifiedAt seen so far). Default 0L.
     * @return [Result.success] on completed sync; [Result.failure] on error.
     */
    suspend operator fun invoke(lessonIds: Set<LessonId>, cursor: Long = 0L): Result<Unit> =
        questions.refreshByParents(lessonIds, cursor)
}
