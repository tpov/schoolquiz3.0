package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "review_assignment_questions",
    primaryKeys = ["ownerUid", "assignmentId", "questionId"],
    indices = [
        Index(value = ["ownerUid"]),
        Index(value = ["assignmentId"]),
        Index(value = ["lessonId"]),
    ],
)
data class ReviewAssignmentQuestionEntity(
    val ownerUid: String,
    val assignmentId: String,
    val questionId: String,
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
