package com.tpov.schoolquiz.shared.feature.theme.data

import com.tpov.schoolquiz.shared.core.persistence.ThemeDao
import com.tpov.schoolquiz.shared.core.persistence.ThemeEntity
import kotlinx.coroutines.flow.Flow

interface ThemeLocalDataSource {
    fun observeBySection(sectionId: String): Flow<List<ThemeEntity>>
    suspend fun upsertByIdIfNewerVersion(entity: ThemeEntity)
    suspend fun upsertFromSyncList(entity: ThemeEntity) = upsertByIdIfNewerVersion(entity)
    suspend fun deleteById(id: String)
    suspend fun findById(id: String): ThemeEntity?
    suspend fun getLocalContentsVersion(id: String): Long?
}

class ThemeLocalDataSourceImpl(
    private val dao: ThemeDao,
) : ThemeLocalDataSource {
    override fun observeBySection(sectionId: String): Flow<List<ThemeEntity>> =
        dao.observeBySection(sectionId)

    override suspend fun upsertByIdIfNewerVersion(entity: ThemeEntity) =
        dao.upsertByIdIfNewerVersion(entity)

    override suspend fun upsertFromSyncList(entity: ThemeEntity) =
        dao.upsertFromSyncList(entity)

    override suspend fun deleteById(id: String) = dao.deleteById(id)

    override suspend fun findById(id: String): ThemeEntity? = dao.findById(id)

    override suspend fun getLocalContentsVersion(id: String): Long? = dao.getContentsVersion(id)
}
