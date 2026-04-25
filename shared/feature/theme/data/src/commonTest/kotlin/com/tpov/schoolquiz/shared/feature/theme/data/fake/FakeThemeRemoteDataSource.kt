package com.tpov.schoolquiz.shared.feature.theme.data.fake

import com.tpov.schoolquiz.shared.feature.theme.data.ThemeRemoteDataSource
import com.tpov.schoolquiz.shared.feature.theme.data.dto.ThemeDto

/**
 * Fake ThemeRemoteDataSource for JVM tests.
 * NOTE: ThemeRemoteDataSource interface will be created by backend-dev in theme/data module.
 */
class FakeThemeRemoteDataSource : ThemeRemoteDataSource {

    var result: List<ThemeDto> = emptyList()
    var shouldThrow: Boolean = false
    var callCount: Int = 0
    var lastSectionIds: Set<String> = emptySet()
    var lastCursor: Long = -1L

    override suspend fun fetchChangedByParents(sectionIds: Set<String>, cursor: Long): List<ThemeDto> {
        callCount++
        lastSectionIds = sectionIds
        lastCursor = cursor
        if (shouldThrow) throw RuntimeException("FakeThemeRemoteDataSource: network error")
        return result
    }
}
