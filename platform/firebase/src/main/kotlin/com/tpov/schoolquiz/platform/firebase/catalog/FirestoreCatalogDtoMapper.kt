package com.tpov.schoolquiz.platform.firebase.catalog

import com.google.firebase.firestore.DocumentSnapshot
import com.tpov.schoolquiz.platform.firebase.util.booleanField
import com.tpov.schoolquiz.platform.firebase.util.longField
import com.tpov.schoolquiz.platform.firebase.util.millisField
import com.tpov.schoolquiz.shared.core.catalog.data.CatalogDto

fun DocumentSnapshot.toCatalogDto(): CatalogDto? {
    val name = getString("name")?.takeIf { it.isNotBlank() }
    val rawPath = getString("picturePath")
    val picturePath = if (rawPath != null && isValidRelativePath(rawPath)) rawPath else null
    val rawIconNames = get("iconNames")
    val iconNames: List<String> =
        when (rawIconNames) {
            is List<*> -> rawIconNames.filterIsInstance<String>().filter { it.isNotBlank() }
            else -> emptyList()
        }
    return if (name != null) {
        CatalogDto(
            id = id,
            name = name,
            picturePath = picturePath,
            version = longField("version") ?: 1L,
            contentsVersion = longField("contentsVersion") ?: 0L,
            lastModifiedAt = millisField("lastModifiedAt") ?: 0L,
            archived = booleanField("archived") ?: false,
            iconCategoryKey = getString("iconCategoryKey")?.takeIf { it.isNotBlank() },
            iconNames = iconNames,
            // Absent for catalogs seeded before types existed; treated as REGULAR downstream.
            questType = getString("questType")?.takeIf { it.isNotBlank() } ?: "REGULAR",
        )
    } else {
        null
    }
}

private fun isValidRelativePath(path: String): Boolean =
    !path.contains("..") &&
        !path.startsWith("/") &&
        !path.startsWith("https://") &&
        !path.startsWith("http://") &&
        !path.startsWith("gs://")
