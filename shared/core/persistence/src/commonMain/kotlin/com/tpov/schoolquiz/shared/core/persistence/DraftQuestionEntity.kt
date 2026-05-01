package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "draft_questions",
    foreignKeys = [
        ForeignKey(
            entity = QuestDraftEntity::class,
            parentColumns = ["id"],
            childColumns = ["draftId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = DraftLessonEntity::class,
            parentColumns = ["id"],
            childColumns = ["lessonId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["draftId"]),
        Index(value = ["lessonId"]),
        Index(value = ["updatedAtMs"]),
    ],
)
data class DraftQuestionEntity(
    @PrimaryKey val id: String,
    val draftId: String,
    val lessonId: String,
    val type: String,
    val language: String,
    val difficulty: String,
    val order: Int,
    val text: String,
    val imagePath: String?,
    val payload: String?,
    val validationState: String,
    val updatedAtMs: Long,
)
