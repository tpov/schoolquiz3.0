package com.tpov.schoolquiz.shared.feature.lesson_runner.data.repository

import com.tpov.schoolquiz.shared.core.persistence.LessonRatingLocalDao
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.mapper.toEntity
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.outbox.LessonResultOutboxWriter
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.LessonRating
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonRatingRepository
import kotlinx.coroutines.flow.Flow

class LessonRatingRepositoryImpl(
    private val ratingLocalDao: LessonRatingLocalDao,
    private val outboxWriter: LessonResultOutboxWriter = LessonResultOutboxWriter.NoOp,
) : LessonRatingRepository {

    override suspend fun submit(rating: LessonRating): Result<Unit> = runCatching {
        val rowId = ratingLocalDao.upsert(rating.toEntity())
        check(rowId > 0) { "upsert returned unexpected rowId: $rowId" }
        outboxWriter.enqueueRating(rating)
    }

    override fun hasSubmitted(userId: String, lessonId: LessonId): Flow<Boolean> =
        ratingLocalDao.hasSubmitted(userId, lessonId.value)
}
