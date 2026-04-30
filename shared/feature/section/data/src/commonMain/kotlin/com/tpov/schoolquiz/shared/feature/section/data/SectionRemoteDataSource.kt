package com.tpov.schoolquiz.shared.feature.section.data

import com.tpov.schoolquiz.shared.feature.section.data.dto.SectionDto

interface SectionRemoteDataSource {
    /** Fetch concrete sections by ids from the flat `sections` collection. */
    suspend fun fetchByIds(ids: Set<String>): List<SectionDto> = emptyList()

    /** Fetch sections whose questId is in questIds and lastModifiedAt > cursor. questIds.size ≤ 30. */
    suspend fun fetchChangedByParents(questIds: Set<String>, cursor: Long): List<SectionDto>
}
