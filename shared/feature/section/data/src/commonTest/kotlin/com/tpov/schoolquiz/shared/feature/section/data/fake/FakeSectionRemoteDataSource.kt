package com.tpov.schoolquiz.shared.feature.section.data.fake

import com.tpov.schoolquiz.shared.feature.section.data.SectionRemoteDataSource
import com.tpov.schoolquiz.shared.feature.section.data.dto.SectionDto

class FakeSectionRemoteDataSource : SectionRemoteDataSource {

    var result: List<SectionDto> = emptyList()
    var shouldThrow: Boolean = false
    var callCount: Int = 0
    var lastQuestIds: Set<String> = emptySet()
    var lastCursor: Long = -1L


    /** Что отдаёт чтение по id: журнал приносит узел именно так. Не вернувшийся id — удалён. */
    var fetchByIdsResult: List<SectionDto> = emptyList()
    var fetchByIdsCallCount: Int = 0
    var lastFetchByIds: Set<String> = emptySet()

    override suspend fun fetchByIds(ids: Set<String>): List<SectionDto> {
        fetchByIdsCallCount++
        lastFetchByIds = ids
        if (shouldThrow) throw RuntimeException("FakeSectionRemoteDataSource: network error")
        return fetchByIdsResult.filter { it.id in ids }
    }

    override suspend fun fetchChangedByParents(questIds: Set<String>, cursor: Long): List<SectionDto> {
        callCount++
        lastQuestIds = questIds
        lastCursor = cursor
        if (shouldThrow) throw RuntimeException("FakeSectionRemoteDataSource: network error")
        return result
    }
}
