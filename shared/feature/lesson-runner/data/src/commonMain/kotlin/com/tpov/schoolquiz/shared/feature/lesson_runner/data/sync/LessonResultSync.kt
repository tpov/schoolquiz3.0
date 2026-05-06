package com.tpov.schoolquiz.shared.feature.lesson_runner.data.sync

import com.tpov.schoolquiz.shared.core.persistence.LessonResultAttemptOutboxEntity
import com.tpov.schoolquiz.shared.core.persistence.LessonResultSyncOutboxDao
import com.tpov.schoolquiz.shared.core.persistence.QuestRatingOutboxEntity
import com.tpov.schoolquiz.shared.core.sync.Syncable
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.LessonResultAttemptEvent
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.LessonResultRemoteDataSource
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.QuestRatingEvent
import kotlinx.coroutines.CancellationException

class LessonResultSync(
    private val outboxDao: LessonResultSyncOutboxDao,
    private val remote: LessonResultRemoteDataSource,
    private val nowMs: () -> Long,
    private val batchSize: Int = DEFAULT_BATCH_SIZE,
) : Syncable {

    override suspend fun sync(): Result<Unit> {
        return try {
            syncAttempts()
            syncRatings()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun syncAttempts() {
        val pending = outboxDao.pendingAttempts(batchSize)
        if (pending.isEmpty()) return
        val ids = pending.map { it.attemptId }
        try {
            remote.submitAttempts(pending.map { it.toRemoteEvent() })
            outboxDao.markAttemptsSent(ids, nowMs())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            outboxDao.markAttemptsFailed(ids, e.syncError())
            throw e
        }
    }

    private suspend fun syncRatings() {
        val pending = outboxDao.pendingRatings(batchSize)
        if (pending.isEmpty()) return
        val ids = pending.map { it.ratingId }
        try {
            remote.submitRatings(pending.map { it.toRemoteEvent() })
            outboxDao.markRatingsSent(ids, nowMs())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            outboxDao.markRatingsFailed(ids, e.syncError())
            throw e
        }
    }

    private fun LessonResultAttemptOutboxEntity.toRemoteEvent(): LessonResultAttemptEvent =
        LessonResultAttemptEvent(
            attemptId = attemptId,
            userId = userId,
            scope = scope,
            ownerUid = ownerUid,
            catalogId = catalogId,
            questId = questId,
            sectionId = sectionId,
            themeId = themeId,
            lessonId = lessonId,
            lessonVersion = lessonVersion,
            sourceShelf = sourceShelf,
            difficulty = difficulty,
            codeAnswer = codeAnswer,
            percentScore = percentScore,
            completedAtMs = completedAtMs,
            createdAtMs = createdAtMs,
        )

    private fun QuestRatingOutboxEntity.toRemoteEvent(): QuestRatingEvent =
        QuestRatingEvent(
            ratingId = ratingId,
            userId = userId,
            scope = scope,
            ownerUid = ownerUid,
            catalogId = catalogId,
            questId = questId,
            sectionId = sectionId,
            themeId = themeId,
            lessonId = lessonId,
            lessonVersion = lessonVersion,
            sourceShelf = sourceShelf,
            rating = rating,
            ratedAtMs = ratedAtMs,
            createdAtMs = createdAtMs,
        )

    private fun Exception.syncError(): String =
        (message ?: toString()).take(MAX_ERROR_LENGTH)

    private companion object {
        const val DEFAULT_BATCH_SIZE = 50
        const val MAX_ERROR_LENGTH = 500
    }
}
