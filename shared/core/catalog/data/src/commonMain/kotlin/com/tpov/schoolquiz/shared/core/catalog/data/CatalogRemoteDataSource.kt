package com.tpov.schoolquiz.shared.core.catalog.data

interface CatalogRemoteDataSource {
    suspend fun fetchChangedSince(cursor: Long): List<CatalogDto>
    suspend fun fetchByIds(ids: Set<String>): List<CatalogDto> = emptyList()
}
