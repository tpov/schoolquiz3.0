package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "quests",
    foreignKeys = [
        ForeignKey(
            entity = CatalogEntity::class,
            parentColumns = ["id"],
            childColumns = ["catalogId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["authorUid"]),
        Index(value = ["catalogId"]),
        Index(value = ["lastModifiedAt"]),
    ],
)
data class QuestEntity(
    @PrimaryKey val id: String,
    val catalogId: String,
    val authorUid: String,
    val title: String,
    val picturePath: String?,
    val pictureUrl: String?,
    val visibleOn: Set<String>,
    val averageRating: Float?,
    val averageRatingCount: Int,
    val version: Long,
    // A legacy NOT NULL column nothing reads any more: refresh is driven by the sync_changes
    // journal, not by a version cascade. Defaulted so no caller has to name it; the column
    // itself goes with the schema rebuild rather than through a migration of its own.
    val contentsVersion: Long = 0L,
    val lastModifiedAt: Long,
    val archived: Boolean,
) {
    init {
        require(pictureUrl == null || pictureUrl.startsWith("https://")) {
            "pictureUrl must be null or an https:// URL, was: $pictureUrl"
        }
        if (picturePath != null) {
            require(
                !picturePath.startsWith("https://") &&
                    !picturePath.startsWith("http://") &&
                    !picturePath.startsWith("gs://"),
            ) { "picturePath must be a relative path, was: $picturePath" }
        }
    }
}
