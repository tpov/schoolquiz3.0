package com.tpov.schoolquiz.shared.feature.section.data

import com.tpov.schoolquiz.shared.core.persistence.SectionDao
import com.tpov.schoolquiz.shared.core.persistence.SectionEntity
import kotlinx.coroutines.flow.Flow

interface SectionLocalDataSource {
    fun observeByQuest(questId: String): Flow<List<SectionEntity>>
    suspend fun upsertByIdIfNewerVersion(entity: SectionEntity)
    suspend fun upsertFromSyncList(entity: SectionEntity) = upsertByIdIfNewerVersion(entity)
    suspend fun deleteById(id: String)
    suspend fun findById(id: String): SectionEntity?
}

class SectionLocalDataSourceImpl(
    private val dao: SectionDao,
) : SectionLocalDataSource {
    override fun observeByQuest(questId: String): Flow<List<SectionEntity>> =
        dao.observeByQuest(questId)

    override suspend fun upsertByIdIfNewerVersion(entity: SectionEntity) =
        dao.upsertByIdIfNewerVersion(entity)

    override suspend fun upsertFromSyncList(entity: SectionEntity) =
        dao.upsertFromSyncList(entity)

    override suspend fun deleteById(id: String) = dao.deleteById(id)

    override suspend fun findById(id: String): SectionEntity? = dao.findById(id)
}
