package com.tpov.schoolquiz.shared.feature.quest.data

import com.tpov.schoolquiz.shared.core.persistence.QuestDao
import com.tpov.schoolquiz.shared.core.persistence.QuestEntity
import kotlinx.coroutines.flow.Flow

interface QuestLocalDataSource {
    fun observeMyQuests(authorUid: String, catalogId: String?): Flow<List<QuestEntity>>
    fun observeByShelf(shelf: String): Flow<List<QuestEntity>>
    fun observeByCatalog(catalogId: String, shelf: String): Flow<List<QuestEntity>>
    fun observeDownloadedArchivedByCatalog(catalogId: String, shelf: String): Flow<List<QuestEntity>> =
        observeByCatalog(catalogId, shelf)
    suspend fun upsertByIdIfNewerVersion(entity: QuestEntity)
    suspend fun upsertFromSyncList(entity: QuestEntity) = upsertByIdIfNewerVersion(entity)
    suspend fun deleteById(id: String)
    suspend fun findById(id: String): QuestEntity?
}

class QuestLocalDataSourceImpl(
    private val dao: QuestDao,
) : QuestLocalDataSource {
    override fun observeMyQuests(authorUid: String, catalogId: String?): Flow<List<QuestEntity>> =
        if (catalogId == null) dao.observeMyQuests(authorUid)
        else dao.observeMyQuestsInCatalog(authorUid, catalogId)

    override fun observeByShelf(shelf: String): Flow<List<QuestEntity>> =
        dao.observeByShelf(shelf)

    override fun observeByCatalog(catalogId: String, shelf: String): Flow<List<QuestEntity>> =
        dao.observeByCatalog(catalogId, shelf)

    override fun observeDownloadedArchivedByCatalog(catalogId: String, shelf: String): Flow<List<QuestEntity>> =
        dao.observeDownloadedArchivedByCatalog(catalogId, shelf)

    override suspend fun upsertByIdIfNewerVersion(entity: QuestEntity) =
        dao.upsertByIdIfNewerVersion(entity)

    override suspend fun upsertFromSyncList(entity: QuestEntity) =
        dao.upsertFromSyncList(entity)

    override suspend fun deleteById(id: String) = dao.deleteById(id)

    override suspend fun findById(id: String): QuestEntity? = dao.findById(id)
}
