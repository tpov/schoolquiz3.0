package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sections",
    foreignKeys = [
        ForeignKey(
            entity = QuestEntity::class,
            parentColumns = ["id"],
            childColumns = ["questId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["questId"]),
        Index(value = ["lastModifiedAt"]),
    ],
)
data class SectionEntity(
    @PrimaryKey val id: String,
    val questId: String,
    val title: String,
    val order: Int,
    val version: Long,
    // A legacy NOT NULL column nothing reads any more: refresh is driven by the sync_changes
    // journal, not by a version cascade. Defaulted so no caller has to name it; the column
    // itself goes with the schema rebuild rather than through a migration of its own.
    val contentsVersion: Long = 0L,
    val lastModifiedAt: Long,
    val archived: Boolean,
)
