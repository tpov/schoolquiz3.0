package com.tpov.schoolquiz.shared.feature.question.data.dto

data class QuestionDto(
    val id: String,
    val lessonId: String,
    val text: String,
    val payload: String,
    val language: String,
    val order: Int,
    val version: Long,
    val lastModifiedAt: Long,
    val archived: Boolean,
)
