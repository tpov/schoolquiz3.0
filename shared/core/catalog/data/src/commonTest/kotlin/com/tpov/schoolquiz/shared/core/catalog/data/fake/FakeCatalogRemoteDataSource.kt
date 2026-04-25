package com.tpov.schoolquiz.shared.core.catalog.data.fake

import com.tpov.schoolquiz.shared.core.catalog.data.CatalogDto
import com.tpov.schoolquiz.shared.core.catalog.data.CatalogRemoteDataSource

class FakeCatalogRemoteDataSource : CatalogRemoteDataSource {
    var result: Result<List<CatalogDto>> = Result.success(emptyList())
    var fetchChangedSinceCalls: Int = 0
    var lastCursor: Long = -1L

    override suspend fun fetchChangedSince(cursor: Long): List<CatalogDto> {
        fetchChangedSinceCalls++
        lastCursor = cursor
        return result.getOrThrow()
    }
}
