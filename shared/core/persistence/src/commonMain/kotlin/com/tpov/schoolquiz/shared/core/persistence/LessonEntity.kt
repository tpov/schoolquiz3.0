package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tpov.schoolquiz.shared.core.leaderboard.TopParticipant

@Entity(
    tableName = "lessons",
    foreignKeys = [
        ForeignKey(
            entity = ThemeEntity::class,
            parentColumns = ["id"],
            childColumns = ["themeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["themeId"]),
        Index(value = ["lastModifiedAt"]),
    ],
)
data class LessonEntity(
    @PrimaryKey val id: String,
    val themeId: String,
    val title: String,
    val order: Int,
    val version: Long,
    val contentsVersion: Long,
    val lastModifiedAt: Long,
    val archived: Boolean,
    @ColumnInfo(name = "average_rating") val averageRating: Float? = null,
    @ColumnInfo(name = "rating_count") val ratingCount: Int = 0,
    @ColumnInfo(name = "top3") val top3: List<TopParticipant> = emptyList(),
)
