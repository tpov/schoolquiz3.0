package com.tpov.schoolquiz.shared.core.persistence

data class QuestDraftSummaryEntity(
    val id: String,
    val catalogId: String,
    val title: String,
    val status: String,
    val questionCount: Int,
    val updatedAtMs: Long,
    val isActive: Boolean,
)
