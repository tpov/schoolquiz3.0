package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "themes",
    foreignKeys = [
        ForeignKey(
            entity = SectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sectionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["sectionId"]),
        Index(value = ["lastModifiedAt"]),
    ],
)
data class ThemeEntity(
    @PrimaryKey val id: String,
    val sectionId: String,
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
