package com.tpov.schoolquiz.shared.feature.lesson_runner.data.repository

import com.tpov.schoolquiz.shared.core.persistence.LessonAttemptDao
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.mapper.toDomain
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.mapper.toEntity
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonAttemptRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LessonAttemptRepositoryImpl(
    private val attemptDao: LessonAttemptDao,
) : LessonAttemptRepository {

    override suspend fun save(attempt: Attempt): Result<Unit> = runCatching {
        val rowId = attemptDao.upsert(attempt.toEntity())
        check(rowId > 0) { "upsert returned unexpected rowId: $rowId" }
    }

    override fun observeByLesson(userId: String, lessonId: LessonId): Flow<List<Attempt>> =
        attemptDao.observeByLesson(userId, lessonId.value)
            .map { list -> list.map { it.toDomain() } }

    override fun observeAllByUser(userId: String): Flow<List<Attempt>> =
        attemptDao.observeAllByUser(userId)
            .map { list -> list.map { it.toDomain() } }
}
