package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model

import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId

/** One comment under a lesson's discussion, read-only from the client's perspective. */
data class LessonComment(
    val id: String,
    val lessonId: LessonId,
    val authorNickname: String,
    val authorAvatarUrl: String? = null,
    val text: String,
    val createdAtMs: Long,
)
