package com.tpov.schoolquiz.shared.feature.lesson.domain.use_case

import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson.domain.repository.LessonRepository
import com.tpov.schoolquiz.shared.feature.theme.domain.model.ThemeId

/**
 * Triggers remote sync for lessons of the given themes.
 *
 * Changed ids come from the `sync_changes` journal; this use case pulls the lessons
 * of the themes it is handed.
 */
class SyncLessonsUseCase(
    private val lessons: LessonRepository,
) {
    /**
     * @param themeIds IDs of themes whose lessons need to be refreshed.
     * @param cursor   local lessonsCursor (max lastModifiedAt seen so far). Default 0L.
     * @return [Result.success] containing the set of processed [LessonId]s for cascade trigger;
     *   [Result.failure] on error.
     */
    suspend operator fun invoke(themeIds: Set<ThemeId>, cursor: Long = 0L): Result<Set<LessonId>> =
        lessons.refreshByParents(themeIds, cursor)
}
