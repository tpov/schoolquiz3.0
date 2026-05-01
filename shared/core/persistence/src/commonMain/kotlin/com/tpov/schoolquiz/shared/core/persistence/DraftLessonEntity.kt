package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "draft_lessons",
    foreignKeys = [
        ForeignKey(
            entity = QuestDraftEntity::class,
            parentColumns = ["id"],
            childColumns = ["draftId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = DraftThemeEntity::class,
            parentColumns = ["id"],
            childColumns = ["themeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["draftId"]),
        Index(value = ["themeId"]),
    ],
)
data class DraftLessonEntity(
    @PrimaryKey val id: String,
    val draftId: String,
    val themeId: String,
    val title: String,
    val order: Int,
)
