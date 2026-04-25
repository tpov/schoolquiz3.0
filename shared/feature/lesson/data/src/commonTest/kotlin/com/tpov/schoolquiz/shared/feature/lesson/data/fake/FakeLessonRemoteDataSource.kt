package com.tpov.schoolquiz.shared.feature.lesson.data.fake

import com.tpov.schoolquiz.shared.feature.lesson.data.LessonRemoteDataSource
import com.tpov.schoolquiz.shared.feature.lesson.data.dto.LessonDto

class FakeLessonRemoteDataSource : LessonRemoteDataSource {

    var result: List<LessonDto> = emptyList()
    var shouldThrow: Boolean = false
    var callCount: Int = 0
    var lastThemeIds: Set<String> = emptySet()
    var lastCursor: Long = -1L

    override suspend fun fetchChangedByParents(themeIds: Set<String>, cursor: Long): List<LessonDto> {
        callCount++
        lastThemeIds = themeIds
        lastCursor = cursor
        if (shouldThrow) throw RuntimeException("FakeLessonRemoteDataSource: network error")
        return result
    }
}
