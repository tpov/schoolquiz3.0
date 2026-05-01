package com.tpov.schoolquiz.shared.core.catalog.data

import com.tpov.schoolquiz.shared.core.catalog.data.mapper.toDomain
import com.tpov.schoolquiz.shared.core.catalog.data.mapper.toEntity
import com.tpov.schoolquiz.shared.core.catalog.domain.model.Catalog
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.core.catalog.domain.repository.CatalogRepository
import com.tpov.schoolquiz.shared.core.sync.CATALOG_LIST_CURSOR_ID
import com.tpov.schoolquiz.shared.core.sync.SyncStateRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class CatalogRepositoryImpl(
    private val local: CatalogLocalDataSource,
    private val remote: CatalogRemoteDataSource,
    private val storageUrlResolver: StorageUrlResolver,
    private val syncStateRepo: SyncStateRepository,
) : CatalogRepository {
    override fun observeAll(): Flow<List<Catalog>> =
        local.observeAll().map { list ->
            list.map { it.toDomain() }.sortedBy { it.id.value }
        }

    override suspend fun refreshFromRemote(): Result<Set<CatalogId>> {
        return try {
            val savedCursor = syncStateRepo.getCursor(CATALOG_LIST_CURSOR_ID)
            val cursor = if (local.observeAll().first().isEmpty()) 0L else savedCursor
            val dtos = remote.fetchChangedSince(cursor)
            val changedIds = mutableSetOf<CatalogId>()
            for (dto in dtos) {
                if (applyDto(dto)) {
                    changedIds.add(CatalogId(dto.id))
                }
            }
            Result.success(changedIds)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun refreshByIds(ids: Set<CatalogId>): Result<Unit> {
        if (ids.isEmpty()) return Result.success(Unit)
        return try {
            val requested = ids.map { it.value }.toSet()
            val dtos = remote.fetchByIds(requested)
            val returned = dtos.map { it.id }.toSet()
            for (missingId in requested - returned) {
                local.deleteById(missingId)
            }
            for (dto in dtos) {
                applyDto(dto)
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getById(id: CatalogId): Catalog? =
        local.findById(id.value)?.toDomain()

    private suspend fun applyDto(dto: CatalogDto): Boolean {
        val baseEntity = dto.toEntity()
        val existing = local.findById(dto.id)
        val path = baseEntity.picturePath
        val retainedUrl =
            existing?.pictureUrl?.takeIf {
                existing.picturePath == path
            }
        val resolvedUrl = when {
            path == null -> null
            retainedUrl != null -> retainedUrl
            !path.startsWith("catalog-pictures/") -> null
            else -> runCatching { storageUrlResolver(path) }
                .onFailure { if (it is CancellationException) throw it }
                .getOrNull()
        }
        val entity = baseEntity.copy(pictureUrl = resolvedUrl)
        return if (dto.archived) {
            val localVersion = existing?.version ?: 0L
            if (dto.version > localVersion) local.deleteById(dto.id)
            false
        } else {
            local.upsertByIdIfNewerVersion(entity)
            true
        }
    }
}
