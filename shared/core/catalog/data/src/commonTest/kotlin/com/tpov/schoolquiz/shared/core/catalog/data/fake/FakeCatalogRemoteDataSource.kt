package com.tpov.schoolquiz.shared.core.catalog.data.fake

import com.tpov.schoolquiz.shared.core.catalog.data.CatalogDto
import com.tpov.schoolquiz.shared.core.catalog.data.CatalogRemoteDataSource
import com.tpov.schoolquiz.shared.core.sync.SyncCursor

class FakeCatalogRemoteDataSource : CatalogRemoteDataSource {
    var result: Result<List<CatalogDto>> = Result.success(emptyList())
    var fetchChangedSinceCalls: Int = 0
    var lastCursor: SyncCursor = SyncCursor(0L, "не спрашивали")
    val cursors = mutableListOf<SyncCursor>()
    var lastLimit: Int = -1

    /** Что отдаёт чтение по id: журнал приносит каталог именно так. Не вернувшийся id — удалён. */
    var fetchByIdsResult: List<CatalogDto> = emptyList()
    var fetchByIdsCallCount: Int = 0
    var lastFetchByIds: Set<String> = emptySet()

    override suspend fun fetchByIds(ids: Set<String>): List<CatalogDto> {
        fetchByIdsCallCount++
        lastFetchByIds = ids
        return fetchByIdsResult.filter { it.id in ids }
    }

    /** Как Firestore: страница начиная с пары, включая её миллисекунду при пустом `docId`. */
    override suspend fun fetchChangedSince(cursor: SyncCursor, limit: Int): List<CatalogDto> {
        fetchChangedSinceCalls++
        lastCursor = cursor
        lastLimit = limit
        cursors += cursor
        val all = result.getOrThrow()
        return if (pagesFromCursor) {
            all.sortedBy { SyncCursor(it.lastModifiedAt, it.id) }
                .filter { SyncCursor(it.lastModifiedAt, it.id) >= cursor }
                .filter { cursor.docId.isEmpty() || SyncCursor(it.lastModifiedAt, it.id) > cursor }
                .take(limit)
        } else {
            all
        }
    }

    /** Отдавать ли страницу от курсора; по умолчанию фейк отдаёт всё, как и раньше. */
    var pagesFromCursor: Boolean = false
}
