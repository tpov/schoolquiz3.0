package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model

import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId

/** One comment under a lesson's discussion, read-only from the client's perspective. */
data class LessonComment(
    val id: String,
    val lessonId: LessonId,
    /**
     * Who wrote it. Nullable only for comments posted before the field existed — everything
     * written now carries it, pinned server-side to the signed-in uid, so a report can name a
     * person and a block can hide them.
     */
    val authorUid: String? = null,
    val authorNickname: String,
    val authorAvatarUrl: String? = null,
    val text: String,
    val createdAtMs: Long,
)
