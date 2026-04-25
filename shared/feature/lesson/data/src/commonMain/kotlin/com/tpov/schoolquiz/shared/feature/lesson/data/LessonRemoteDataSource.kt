package com.tpov.schoolquiz.shared.feature.lesson.data

import com.tpov.schoolquiz.shared.feature.lesson.data.dto.LessonDto

interface LessonRemoteDataSource {
    /** Fetch lessons whose themeId is in themeIds and lastModifiedAt > cursor. themeIds.size ≤ 30. */
    suspend fun fetchChangedByParents(themeIds: Set<String>, cursor: Long): List<LessonDto>
}
