package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository

import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.LessonComment
import kotlinx.coroutines.flow.Flow

interface LessonCommentRepository {
    /** Newest-last stream of comments for [lessonId]. */
    fun observe(lessonId: LessonId): Flow<List<LessonComment>>

    /** Publishes a comment for [lessonId]. Returns failure on a network error. */
    suspend fun post(
        lessonId: LessonId,
        authorNickname: String,
        authorAvatarUrl: String?,
        text: String,
    ): Result<Unit>
}
