package com.tpov.schoolquiz.shared.feature.theme.data

import com.tpov.schoolquiz.shared.feature.theme.data.dto.ThemeDto

interface ThemeRemoteDataSource {
    /** Fetch themes whose sectionId is in sectionIds and lastModifiedAt > cursor. sectionIds.size ≤ 30. */
    suspend fun fetchChangedByParents(sectionIds: Set<String>, cursor: Long): List<ThemeDto>
}
