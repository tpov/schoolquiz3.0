package com.tpov.schoolquiz.shared.feature.theme.data

import com.tpov.schoolquiz.shared.feature.theme.data.dto.ThemeDto

interface ThemeRemoteDataSource {
    /** Fetch concrete themes by ids from the flat `themes` collection. */
    suspend fun fetchByIds(ids: Set<String>): List<ThemeDto> = emptyList()

    /** Fetch themes whose sectionId is in sectionIds and lastModifiedAt > cursor. sectionIds.size ≤ 30. */
    suspend fun fetchChangedByParents(sectionIds: Set<String>, cursor: Long): List<ThemeDto>
}
