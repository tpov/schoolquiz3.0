package com.tpov.schoolquiz.shared.feature.lesson.data

import com.tpov.schoolquiz.shared.feature.lesson.data.mapper.LessonDtoMapper.toEntity
import com.tpov.schoolquiz.shared.feature.lesson.data.mapper.LessonMapper.toDomain
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.Lesson
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson.domain.repository.LessonRepository
import com.tpov.schoolquiz.shared.feature.theme.domain.model.ThemeId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LessonRepositoryImpl(
    private val local: LessonLocalDataSource,
    private val remote: LessonRemoteDataSource,
) : LessonRepository {

    override fun observeByTheme(themeId: ThemeId): Flow<List<Lesson>> =
        local.observeByTheme(themeId.value).map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: LessonId): Lesson? =
        local.findById(id.value)?.toDomain()

    override suspend fun getLocalContentsVersion(id: LessonId): Long? =
        local.getLocalContentsVersion(id.value)

    override suspend fun refreshByParents(themeIds: Set<ThemeId>, cursor: Long): Result<Set<LessonId>> {
        if (themeIds.isEmpty()) return Result.success(emptySet())
        return try {
            val dtos = remote.fetchChangedByParents(themeIds.map { it.value }.toSet(), cursor)
            val changedIds = mutableSetOf<LessonId>()
            for (dto in dtos) {
                val localEntity = local.findById(dto.id)
                if (dto.archived) {
                    val localVersion = localEntity?.version ?: 0L
                    if (dto.version > localVersion) local.deleteById(dto.id)
                } else {
                    local.upsertByIdIfNewerVersion(dto.toEntity())
                    if (localEntity == null || dto.contentsVersion > localEntity.contentsVersion) {
                        changedIds.add(LessonId(dto.id))
                    }
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
