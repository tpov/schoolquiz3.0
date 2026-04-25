package com.tpov.schoolquiz.shared.feature.lesson.data.dto

data class LessonDto(
    val id: String,
    val themeId: String,
    val title: String,
    val order: Int,
    val version: Long,
    val contentsVersion: Long,
    val lastModifiedAt: Long,
    val archived: Boolean,
)
