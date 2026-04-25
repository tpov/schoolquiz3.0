package com.tpov.schoolquiz.shared.core.catalog.data

import com.tpov.schoolquiz.shared.core.persistence.CatalogDao
import com.tpov.schoolquiz.shared.core.persistence.CatalogEntity
import kotlinx.coroutines.flow.Flow

interface CatalogLocalDataSource {
    fun observeAll(): Flow<List<CatalogEntity>>
    suspend fun replaceAll(entities: List<CatalogEntity>)
    suspend fun findById(id: String): CatalogEntity?
    suspend fun upsertByIdIfNewerVersion(entity: CatalogEntity)
    suspend fun deleteById(id: String)
}

class CatalogLocalDataSourceImpl(
    private val dao: CatalogDao,
) : CatalogLocalDataSource {
    override fun observeAll(): Flow<List<CatalogEntity>> = dao.observeAll()
    override suspend fun replaceAll(entities: List<CatalogEntity>) = dao.replaceAll(entities)
    override suspend fun findById(id: String): CatalogEntity? = dao.findById(id)
    override suspend fun upsertByIdIfNewerVersion(entity: CatalogEntity) =
        dao.upsertByIdIfNewerVersion(entity)
    override suspend fun deleteById(id: String) = dao.deleteById(id)
}
