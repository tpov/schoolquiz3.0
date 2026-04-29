package com.tpov.schoolquiz.shared.core.catalog.data

data class CatalogDto(
    val id: String,
    val name: String,
    val picturePath: String?,
    val version: Long,
    val contentsVersion: Long,
    val lastModifiedAt: Long,
    val archived: Boolean,
    val iconCategoryKey: String? = null,
    val iconNames: List<String> = emptyList(),
)
