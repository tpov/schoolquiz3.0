package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "draft_sections",
    foreignKeys = [
        ForeignKey(
            entity = QuestDraftEntity::class,
            parentColumns = ["id"],
            childColumns = ["draftId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["draftId"]),
    ],
)
data class DraftSectionEntity(
    @PrimaryKey val id: String,
    val draftId: String,
    val title: String,
    val order: Int,
)
