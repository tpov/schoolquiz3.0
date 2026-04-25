package com.tpov.schoolquiz.platform.firebase.catalog

import com.google.firebase.firestore.DocumentSnapshot
import com.tpov.schoolquiz.shared.core.catalog.data.CatalogDto

fun DocumentSnapshot.toCatalogDto(): CatalogDto? {
    val name = getString("name") ?: return null
    if (name.isBlank()) return null
    val rawPath = getString("picturePath")
    val picturePath = if (rawPath != null && isValidRelativePath(rawPath)) rawPath else null
    return CatalogDto(
        id = id,
        name = name,
        picturePath = picturePath,
        version = getLong("version") ?: 1L,
        contentsVersion = getLong("contentsVersion") ?: 0L,
        lastModifiedAt = getTimestamp("lastModifiedAt")?.toDate()?.time ?: 0L,
        archived = getBoolean("archived") ?: false,
    )
}

private fun isValidRelativePath(path: String): Boolean =
    !path.contains("..") &&
        !path.startsWith("/") &&
        !path.startsWith("https://") &&
        !path.startsWith("http://") &&
        !path.startsWith("gs://")
