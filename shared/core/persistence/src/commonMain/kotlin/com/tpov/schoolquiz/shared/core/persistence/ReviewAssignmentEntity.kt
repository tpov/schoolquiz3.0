package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "review_assignments",
    primaryKeys = ["ownerUid", "id"],
    indices = [
        Index(value = ["ownerUid"]),
        Index(value = ["lessonId"]),
    ],
)
data class ReviewAssignmentEntity(
    val id: String,
    val ownerUid: String,
    val submissionId: String,
    val catalogId: String,
    val draftId: String,
    val questId: String,
    val lessonId: String,
    val title: String,
    val createdAtMs: Long,
    val taskKinds: List<String>,
    val sourceLanguages: List<String>,
    val newTranslationLanguages: List<String>,
    val reviewLanguages: List<String>,
    val isTested: Boolean,
    val testingScore: Double?,
    val isLogicReviewed: Boolean,
    val logicScore: Double?,
    val isTranslationReviewed: Boolean,
    val translationScore: Int?,
    val translatedLanguages: List<String>,
)
