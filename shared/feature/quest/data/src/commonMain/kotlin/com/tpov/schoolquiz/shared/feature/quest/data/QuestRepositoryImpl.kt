package com.tpov.schoolquiz.shared.feature.quest.data

import com.tpov.schoolquiz.shared.core.catalog.data.StorageUrlResolver
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.feature.quest.data.mapper.QuestDtoMapper.toEntity
import com.tpov.schoolquiz.shared.feature.quest.data.mapper.QuestMapper.toDomain
import com.tpov.schoolquiz.shared.feature.quest.domain.model.Quest
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import com.tpov.schoolquiz.shared.feature.quest.domain.repository.QuestRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class QuestRepositoryImpl(
    private val local: QuestLocalDataSource,
    private val remote: QuestRemoteDataSource,
    private val storageUrlResolver: StorageUrlResolver,
) : QuestRepository {

    override fun observeMyQuests(authorUid: String, catalogId: CatalogId?): Flow<List<Quest>> =
        local.observeMyQuests(authorUid, catalogId?.value).map { list ->
            list.map { it.toDomain() }
        }

    override fun observeByShelf(shelf: String): Flow<List<Quest>> =
        local.observeByShelf(shelf).map { list -> list.map { it.toDomain() } }

    override fun observeByCatalog(catalogId: CatalogId, shelf: String): Flow<List<Quest>> =
        local.observeByCatalog(catalogId.value, shelf).map { list -> list.map { it.toDomain() } }

    override fun observeDownloadedArchivedByCatalog(catalogId: CatalogId, shelf: String): Flow<List<Quest>> =
        local.observeDownloadedArchivedByCatalog(catalogId.value, shelf).map { list ->
            list.map { it.toDomain() }
        }

    override suspend fun getById(id: QuestId): Quest? =
        local.findById(id.value)?.toDomain()

    override suspend fun refreshByIds(ids: Set<QuestId>): Result<Unit> {
        if (ids.isEmpty()) return Result.success(Unit)
        return try {
            val requested = ids.map { it.value }.toSet()
            val dtos = remote.fetchByIds(requested)
            val returned = dtos.map { it.id }.toSet()
            for (missingId in requested - returned) {
                local.deleteById(missingId)
            }
            for (dto in dtos) {
                applyDto(dto, fromSyncList = true)
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun refreshFromRemote(
        currentUserUid: String?,
        availableShelves: Set<String>,
        catalogIdsToSync: Set<CatalogId>,
        cursor: Long,
    ): Result<Set<QuestId>> {
        if (catalogIdsToSync.isEmpty()) return Result.success(emptySet())
        return try {
            val catalogIdStrings = catalogIdsToSync.map { it.value }.toSet()
            val dtos = coroutineScope {
                // Query A skipped when currentUserUid is null (guest mode — ADR-CMP-49 #2)
                val queryA = async {
                    if (currentUserUid == null) emptyList()
                    else remote.fetchOwnChanged(currentUserUid, catalogIdStrings, cursor)
                }
                val queryB = async {
                    remote.fetchPublicChanged(availableShelves, cursor)
                        .filter { it.catalogId in catalogIdStrings }
                }
                // A wins on id conflict: own quests take priority over public results
                (queryA.await() + queryB.await()).distinctBy { it.id }
            }
            val changedIds = mutableSetOf<QuestId>()
            for (dto in dtos) {
                if (applyDto(dto, fromSyncList = false)) {
                    changedIds.add(QuestId(dto.id))
                }
            }
            Result.success(changedIds)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun applyDto(
        dto: com.tpov.schoolquiz.shared.feature.quest.data.dto.QuestDto,
        fromSyncList: Boolean,
    ): Boolean {
        val localEntity = local.findById(dto.id)
        val path = dto.picturePath
        val retainedUrl =
            localEntity?.pictureUrl?.takeIf {
                localEntity.picturePath == path
            }
        val resolvedUrl = when {
            path == null -> null
            retainedUrl != null -> retainedUrl
            else -> runCatching { storageUrlResolver(path) }
                .onFailure { if (it is CancellationException) throw it }
                .getOrNull()
        }
        val entity = dto.toEntity(resolvedUrl)
        return if (dto.visibleOn.isEmpty()) {
            val localVersion = localEntity?.version ?: 0L
            if (fromSyncList || dto.version > localVersion) local.deleteById(dto.id)
            false
        } else {
            if (fromSyncList) local.upsertFromSyncList(entity) else local.upsertByIdIfNewerVersion(entity)
            true
        }
    }
}
