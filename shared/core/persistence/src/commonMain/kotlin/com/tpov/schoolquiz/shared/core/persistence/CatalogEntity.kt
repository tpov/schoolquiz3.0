package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "catalogs",
    indices = [Index(value = ["lastModifiedAt"])],
)
data class CatalogEntity(
    @PrimaryKey val id: String,
    val name: String,
    val picturePath: String?,
    val pictureUrl: String?,
    val version: Long = 1L,
    val contentsVersion: Long = 0L,
    val lastModifiedAt: Long = 0L,
    val archived: Boolean = false,
    val iconCategoryKey: String? = null,
    val iconNames: List<String> = emptyList(),
    val questType: String = "REGULAR",
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
        require(version >= 1L) { "version >= 1, got $version" }
        require(contentsVersion >= 0L) { "contentsVersion >= 0" }
        require(lastModifiedAt >= 0L) { "lastModifiedAt >= 0" }
    }
}
