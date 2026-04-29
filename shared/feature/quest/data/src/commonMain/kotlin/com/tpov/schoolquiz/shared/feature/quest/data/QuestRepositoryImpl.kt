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

    override suspend fun getById(id: QuestId): Quest? =
        local.findById(id.value)?.toDomain()

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
                val localEntity = local.findById(dto.id)
                val path = dto.picturePath
                val resolvedUrl = when {
                    path == null -> null
                    else -> runCatching { storageUrlResolver(path) }
                        .onFailure { if (it is CancellationException) throw it }
                        .getOrNull()
                }
                val entity = dto.toEntity(resolvedUrl)
                if (dto.archived || dto.visibleOn.isEmpty()) {
                    val localVersion = localEntity?.version ?: 0L
                    if (dto.version > localVersion) local.deleteById(dto.id)
                } else {
                    local.upsertByIdIfNewerVersion(entity)
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
}
