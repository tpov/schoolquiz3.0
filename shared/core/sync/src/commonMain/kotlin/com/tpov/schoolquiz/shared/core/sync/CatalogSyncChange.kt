package com.tpov.schoolquiz.shared.core.sync

import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId

data class CatalogSyncChange(
    val catalogId: CatalogId,
    val type: CatalogSyncNodeType,
    val nodeId: String,
    val changedAtMs: Long,
)

enum class CatalogSyncNodeType {
    Catalog,
    Quest,
    Section,
    Theme,
    Lesson,
    Question,
}

interface CatalogSyncChangeRemoteDataSource {
    suspend fun fetchChangedSince(catalogId: CatalogId, cursorMs: Long): List<CatalogSyncChange>
}

const val CATALOG_LIST_CURSOR_ID: String = "catalogs"

fun catalogSyncCursorId(catalogId: CatalogId): String = "catalog_sync:${catalogId.value}"
