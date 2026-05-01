package com.tpov.schoolquiz.shared.core.sync

import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId

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

data class LessonContentSyncChange(
    val lessonId: LessonId,
    val type: CatalogSyncNodeType,
    val nodeId: String,
    val changedAtMs: Long,
)

interface LessonContentSyncChangeRemoteDataSource {
    suspend fun fetchChangedSince(lessonId: LessonId, cursorMs: Long): List<LessonContentSyncChange>
}

const val CATALOG_LIST_CURSOR_ID: String = "catalogs"

fun catalogSyncCursorId(catalogId: CatalogId): String = "catalog_sync:${catalogId.value}"

fun lessonContentSyncCursorId(lessonId: LessonId): String = "lesson_content:${lessonId.value}"
