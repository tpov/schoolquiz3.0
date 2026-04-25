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
    val contentsVersion: Long,
    val lastModifiedAt: Long,
    val archived: Boolean,
)
