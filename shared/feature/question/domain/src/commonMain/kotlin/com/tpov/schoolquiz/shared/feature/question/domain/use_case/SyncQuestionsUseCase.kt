package com.tpov.schoolquiz.shared.feature.question.domain.use_case

import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.question.domain.repository.QuestionRepository

/**
 * Triggers remote sync for questions of the given lessons.
 *
 * Cascading sync step 6 (leaf): after lessons with grown contentsVersion are
 * synced, pull their questions.
 *
 * Spec: docs/features/home-and-my-quests/0-spec.md — FR#14 cascading sync step 6.
 * Primary User Journey 5: admin adds question → cascades to leaf.
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
