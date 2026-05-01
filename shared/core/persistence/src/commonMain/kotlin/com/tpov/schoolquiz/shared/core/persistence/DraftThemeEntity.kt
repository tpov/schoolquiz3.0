package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "draft_themes",
    foreignKeys = [
        ForeignKey(
            entity = QuestDraftEntity::class,
            parentColumns = ["id"],
            childColumns = ["draftId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = DraftSectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sectionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["draftId"]),
        Index(value = ["sectionId"]),
    ],
)
data class DraftThemeEntity(
    @PrimaryKey val id: String,
    val draftId: String,
    val sectionId: String,
    val title: String,
    val order: Int,
)
