package com.tpov.schoolquiz.shared.feature.theme.data

import com.tpov.schoolquiz.shared.feature.theme.data.mapper.ThemeDtoMapper.toEntity
import com.tpov.schoolquiz.shared.feature.theme.data.mapper.ThemeMapper.toDomain
import com.tpov.schoolquiz.shared.feature.section.domain.model.SectionId
import com.tpov.schoolquiz.shared.feature.theme.domain.model.Theme
import com.tpov.schoolquiz.shared.feature.theme.domain.model.ThemeId
import com.tpov.schoolquiz.shared.feature.theme.domain.repository.ThemeRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ThemeRepositoryImpl(
    private val local: ThemeLocalDataSource,
    private val remote: ThemeRemoteDataSource,
) : ThemeRepository {

    override fun observeBySection(sectionId: SectionId): Flow<List<Theme>> =
        local.observeBySection(sectionId.value).map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: ThemeId): Theme? =
        local.findById(id.value)?.toDomain()

    override suspend fun getLocalContentsVersion(id: ThemeId): Long? =
        local.getLocalContentsVersion(id.value)

    override suspend fun refreshByParents(sectionIds: Set<SectionId>, cursor: Long): Result<Set<ThemeId>> {
        if (sectionIds.isEmpty()) return Result.success(emptySet())
        return try {
            val dtos = remote.fetchChangedByParents(sectionIds.map { it.value }.toSet(), cursor)
            val changedIds = mutableSetOf<ThemeId>()
            for (dto in dtos) {
                val localEntity = local.findById(dto.id)
                if (dto.archived) {
                    val localVersion = localEntity?.version ?: 0L
                    if (dto.version > localVersion) local.deleteById(dto.id)
                } else {
                    local.upsertByIdIfNewerVersion(dto.toEntity())
                    changedIds.add(ThemeId(dto.id))
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
