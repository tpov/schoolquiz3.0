package com.tpov.schoolquiz.shared.feature.section.data

import com.tpov.schoolquiz.shared.feature.section.data.mapper.SectionDtoMapper.toEntity
import com.tpov.schoolquiz.shared.feature.section.data.mapper.SectionMapper.toDomain
import com.tpov.schoolquiz.shared.feature.section.domain.model.Section
import com.tpov.schoolquiz.shared.feature.section.domain.model.SectionId
import com.tpov.schoolquiz.shared.feature.section.domain.repository.SectionRepository
import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SectionRepositoryImpl(
    private val local: SectionLocalDataSource,
    private val remote: SectionRemoteDataSource,
) : SectionRepository {

    override fun observeByQuest(questId: QuestId): Flow<List<Section>> =
        local.observeByQuest(questId.value).map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: SectionId): Section? =
        local.findById(id.value)?.toDomain()

    override suspend fun getLocalContentsVersion(id: SectionId): Long? =
        local.getLocalContentsVersion(id.value)

    override suspend fun refreshByIds(ids: Set<SectionId>): Result<Unit> {
        if (ids.isEmpty()) return Result.success(Unit)
        return try {
            val requested = ids.map { it.value }.toSet()
            val dtos = remote.fetchByIds(requested)
            val returned = dtos.map { it.id }.toSet()
            for (missingId in requested - returned) {
                local.deleteById(missingId)
            }
            for (dto in dtos) {
                if (dto.archived) {
                    local.deleteById(dto.id)
                } else {
                    local.upsertFromSyncList(dto.toEntity())
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun refreshByParents(questIds: Set<QuestId>, cursor: Long): Result<Set<SectionId>> {
        if (questIds.isEmpty()) return Result.success(emptySet())
        return try {
            val dtos = remote.fetchChangedByParents(questIds.map { it.value }.toSet(), cursor)
            val changedIds = mutableSetOf<SectionId>()
            for (dto in dtos) {
                val localEntity = local.findById(dto.id)
                if (dto.archived) {
                    val localVersion = localEntity?.version ?: 0L
                    if (dto.version > localVersion) local.deleteById(dto.id)
                } else {
                    local.upsertByIdIfNewerVersion(dto.toEntity())
                    changedIds.add(SectionId(dto.id))
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
