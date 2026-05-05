package com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model

data class ReviewQuestion(
    val id: String,
    val draftId: String,
    val lessonId: String,
    val type: String,
    val language: String,
    val languageLevel: Int,
    val difficulty: String,
    val order: Int,
    val text: String,
    val imagePath: String?,
    val payload: String,
    val updatedAtMs: Long,
)
