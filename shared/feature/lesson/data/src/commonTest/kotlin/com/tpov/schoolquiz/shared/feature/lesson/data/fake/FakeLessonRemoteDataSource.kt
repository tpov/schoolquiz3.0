package com.tpov.schoolquiz.shared.feature.lesson.data.fake

import com.tpov.schoolquiz.shared.feature.lesson.data.LessonRemoteDataSource
import com.tpov.schoolquiz.shared.feature.lesson.data.dto.LessonDto

class FakeLessonRemoteDataSource : LessonRemoteDataSource {

    var result: List<LessonDto> = emptyList()
    var shouldThrow: Boolean = false
    var callCount: Int = 0
    var lastThemeIds: Set<String> = emptySet()
    var lastCursor: Long = -1L


    /** Что отдаёт чтение по id: журнал приносит узел именно так. Не вернувшийся id — удалён. */
    var fetchByIdsResult: List<LessonDto> = emptyList()
    var fetchByIdsCallCount: Int = 0
    var lastFetchByIds: Set<String> = emptySet()

    override suspend fun fetchByIds(ids: Set<String>): List<LessonDto> {
        fetchByIdsCallCount++
        lastFetchByIds = ids
        if (shouldThrow) throw RuntimeException("FakeLessonRemoteDataSource: network error")
        return fetchByIdsResult.filter { it.id in ids }
    }

    override suspend fun fetchChangedByParents(themeIds: Set<String>, cursor: Long): List<LessonDto> {
        callCount++
        lastThemeIds = themeIds
        lastCursor = cursor
        if (shouldThrow) throw RuntimeException("FakeLessonRemoteDataSource: network error")
        return result
    }
}
