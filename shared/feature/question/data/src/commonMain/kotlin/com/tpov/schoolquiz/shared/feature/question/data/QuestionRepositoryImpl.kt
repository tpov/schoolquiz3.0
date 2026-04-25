package com.tpov.schoolquiz.shared.feature.question.data

import com.tpov.schoolquiz.shared.feature.question.data.mapper.QuestionDtoMapper.toEntity
import com.tpov.schoolquiz.shared.feature.question.data.mapper.QuestionMapper.toDomain
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.question.domain.model.Question
import com.tpov.schoolquiz.shared.feature.question.domain.model.QuestionId
import com.tpov.schoolquiz.shared.feature.question.domain.repository.QuestionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class QuestionRepositoryImpl(
    private val local: QuestionLocalDataSource,
    private val remote: QuestionRemoteDataSource,
) : QuestionRepository {

    override fun observeByLesson(lessonId: LessonId): Flow<List<Question>> =
        local.observeByLesson(lessonId.value).map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: QuestionId): Question? =
        local.findById(id.value)?.toDomain()

    override suspend fun refreshByParents(lessonIds: Set<LessonId>, cursor: Long): Result<Unit> {
        if (lessonIds.isEmpty()) return Result.success(Unit)
        return try {
            val dtos = remote.fetchChangedByParents(lessonIds.map { it.value }.toSet(), cursor)
            for (dto in dtos) {
                if (dto.archived) {
                    val localVersion = local.findById(dto.id)?.version ?: 0L
                    if (dto.version > localVersion) local.deleteById(dto.id)
                } else {
                    local.upsertByIdIfNewerVersion(dto.toEntity())
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
