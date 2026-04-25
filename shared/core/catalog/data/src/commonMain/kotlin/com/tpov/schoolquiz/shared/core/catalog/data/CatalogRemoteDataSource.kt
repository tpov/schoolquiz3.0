package com.tpov.schoolquiz.shared.core.catalog.data

interface CatalogRemoteDataSource {
    suspend fun fetchChangedSince(cursor: Long): List<CatalogDto>
}
