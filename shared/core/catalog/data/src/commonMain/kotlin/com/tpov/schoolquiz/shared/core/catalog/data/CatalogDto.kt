package com.tpov.schoolquiz.shared.core.catalog.data

data class CatalogDto(
    val id: String,
    val name: String,
    val picturePath: String?,
    val version: Long,
    val lastModifiedAt: Long,
    val archived: Boolean,
    val iconCategoryKey: String? = null,
    val iconNames: List<String> = emptyList(),
    /** Stored as a string so an unknown value from a newer client degrades instead of failing. */
    val questType: String = "REGULAR",
)
