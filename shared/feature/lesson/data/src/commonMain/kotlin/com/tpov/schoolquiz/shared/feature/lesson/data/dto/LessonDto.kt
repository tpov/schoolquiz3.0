package com.tpov.schoolquiz.shared.feature.lesson.data.dto

import com.tpov.schoolquiz.shared.core.leaderboard.TopParticipant

data class LessonDto(
    val id: String,
    val themeId: String,
    val title: String,
    val order: Int,
    val version: Long,
    val contentsVersion: Long,
    val lastModifiedAt: Long,
    val archived: Boolean,
    val averageRating: Float? = null,
    val ratingCount: Int? = null,
    val top3: List<TopParticipant>? = null,
)
