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

    /**
     * Одна страница журнала, начиная сразу после [cursor] (AD-31).
     *
     * Читать журнал целиком нельзя: полная перепубликация большого курса приходит одним ответом,
     * и его размер ограничен только тем, сколько успел изменить автор. Реализация по умолчанию
     * оставлена ради фейков в тестах — прод обязан её переопределить.
     */
    suspend fun fetchPage(
        catalogId: CatalogId,
        cursor: SyncCursor,
        limit: Int = SYNC_PAGE_SIZE,
    ): SyncChangePage<CatalogSyncChange> {
        val changes = fetchChangedSince(catalogId, cursor.changedAtMs).take(limit)
        return changes.toPage(limit) { SyncCursor(it.changedAtMs) }
    }
}

data class LessonContentSyncChange(
    val lessonId: LessonId,
    val type: CatalogSyncNodeType,
    val nodeId: String,
    val changedAtMs: Long,
)

interface LessonContentSyncChangeRemoteDataSource {
    suspend fun fetchChangedSince(lessonId: LessonId, cursorMs: Long): List<LessonContentSyncChange>

    /** Одна страница журнала урока. См. [CatalogSyncChangeRemoteDataSource.fetchPage]. */
    suspend fun fetchPage(
        lessonId: LessonId,
        cursor: SyncCursor,
        limit: Int = SYNC_PAGE_SIZE,
    ): SyncChangePage<LessonContentSyncChange> {
        val changes = fetchChangedSince(lessonId, cursor.changedAtMs).take(limit)
        return changes.toPage(limit) { SyncCursor(it.changedAtMs) }
    }
}

/**
 * Собирает страницу из прочитанного.
 *
 * Полная страница означает «скорее всего есть ещё»: журнал мог кончиться ровно на границе, и
 * лишний пустой заход дешевле пропущенной записи.
 */
fun <T> List<T>.toPage(
    limit: Int,
    cursorOf: (T) -> SyncCursor,
): SyncChangePage<T> =
    if (isEmpty()) {
        SyncChangePage.empty()
    } else {
        SyncChangePage(
            changes = this,
            // Максимум, а не последний: порядок гарантирует запрос, но фейк в тесте — нет, и
            // курсор, вставший ниже уже прочитанного, отдал бы те же записи снова.
            nextCursor = maxOf { cursorOf(it) },
            hasMore = size >= limit,
        )
    }

const val CATALOG_LIST_CURSOR_ID: String = "catalogs"

fun catalogSyncCursorId(catalogId: CatalogId): String = "catalog_sync:${catalogId.value}"

fun lessonContentSyncCursorId(lessonId: LessonId): String = "lesson_content:${lessonId.value}"
