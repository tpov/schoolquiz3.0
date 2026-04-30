package com.tpov.schoolquiz.shared.feature.lesson.data

import com.tpov.schoolquiz.shared.feature.lesson.data.dto.LessonDto

interface LessonRemoteDataSource {
    /** Fetch concrete lessons by ids from the flat `lessons` collection. */
    suspend fun fetchByIds(ids: Set<String>): List<LessonDto> = emptyList()

    /** Fetch lessons whose themeId is in themeIds and lastModifiedAt > cursor. themeIds.size ≤ 30. */
    suspend fun fetchChangedByParents(themeIds: Set<String>, cursor: Long): List<LessonDto>
}
