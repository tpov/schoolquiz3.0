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
    /**
     * Одна страница журнала `catalogs/{catalogId}/sync_changes`, начиная сразу после [cursor]
     * (AD-31).
     *
     * Читать журнал целиком нельзя: полная перепубликация большого курса приходит одним ответом,
     * и его размер ограничен только тем, сколько успел изменить автор. Продолжение — по паре
     * «время, id документа», а не по одному времени: две записи в одну миллисекунду иначе
     * сливались бы.
     *
     * Реализация, которая разбирает документы сама, обязана назвать [SyncChangePage.unreadable] —
     * терпимость к трём формам снимается только когда видно, что пропусков не осталось (AD-11).
     */
    suspend fun fetchPage(
        catalogId: CatalogId,
        cursor: SyncCursor,
        limit: Int = SYNC_PAGE_SIZE,
    ): SyncChangePage<CatalogSyncChange>
}

data class LessonContentSyncChange(
    val lessonId: LessonId,
    val type: CatalogSyncNodeType,
    val nodeId: String,
    val changedAtMs: Long,
)

interface LessonContentSyncChangeRemoteDataSource {
    /** Одна страница журнала `lesson_content/{lessonId}/sync_changes`. См. [CatalogSyncChangeRemoteDataSource.fetchPage]. */
    suspend fun fetchPage(
        lessonId: LessonId,
        cursor: SyncCursor,
        limit: Int = SYNC_PAGE_SIZE,
    ): SyncChangePage<LessonContentSyncChange>
}

const val CATALOG_LIST_CURSOR_ID: String = "catalogs"

fun catalogSyncCursorId(catalogId: CatalogId): String = "catalog_sync:${catalogId.value}"

/**
 * Cursor for the complete catalog payload, including questions.
 *
 * This deliberately starts independently from the legacy `catalog_sync` cursor: older clients
 * could advance that cursor while reading structure only, so reusing it could permanently skip
 * question changes on an upgraded installation.
 */
fun catalogContentSyncCursorId(catalogId: CatalogId): String = "catalog_content:${catalogId.value}"

fun lessonContentSyncCursorId(lessonId: LessonId): String = "lesson_content:${lessonId.value}"
