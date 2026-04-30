package com.tpov.schoolquiz.platform.firebase.catalog

import com.google.firebase.firestore.DocumentSnapshot
import com.tpov.schoolquiz.shared.core.catalog.data.CatalogDto

fun DocumentSnapshot.toCatalogDto(): CatalogDto? {
    val name = getString("name") ?: return null
    if (name.isBlank()) return null
    val rawPath = getString("picturePath")
    val picturePath = if (rawPath != null && isValidRelativePath(rawPath)) rawPath else null
    val rawIconNames = get("iconNames")
    val iconNames: List<String> =
        when (rawIconNames) {
            is List<*> -> rawIconNames.filterIsInstance<String>().filter { it.isNotBlank() }
            else -> emptyList()
        }
    return CatalogDto(
        id = id,
        name = name,
        picturePath = picturePath,
        version = getLong("version") ?: 1L,
        contentsVersion = getLong("contentsVersion") ?: 0L,
        lastModifiedAt = getTimestamp("lastModifiedAt")?.toDate()?.time ?: 0L,
        archived = getBoolean("archived") ?: false,
        iconCategoryKey = getString("iconCategoryKey")?.takeIf { it.isNotBlank() },
        iconNames = iconNames,
    )
}

private fun isValidRelativePath(path: String): Boolean =
    !path.contains("..") &&
        !path.startsWith("/") &&
        !path.startsWith("https://") &&
        !path.startsWith("http://") &&
        !path.startsWith("gs://")
