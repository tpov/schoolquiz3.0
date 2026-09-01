package com.tpov.schoolquiz.shared.feature.lesson_runner.data.sync

import com.tpov.schoolquiz.shared.core.persistence.LessonResultAttemptOutboxEntity
import com.tpov.schoolquiz.shared.core.persistence.LessonResultSyncOutboxDao
import com.tpov.schoolquiz.shared.core.persistence.QuestionAnswerDao
import com.tpov.schoolquiz.shared.core.persistence.QuestRatingOutboxEntity
import com.tpov.schoolquiz.shared.core.sync.Syncable
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.LessonAnswerEvent
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.LessonResultAttemptEvent
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.LessonResultRemoteDataSource
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.QuestRatingEvent
import kotlinx.coroutines.CancellationException

class LessonResultSync(
    private val outboxDao: LessonResultSyncOutboxDao,
    private val remote: LessonResultRemoteDataSource,
    private val nowMs: () -> Long,
    private val batchSize: Int = DEFAULT_BATCH_SIZE,
    private val answerDao: QuestionAnswerDao? = null,
) : Syncable {

    /**
     * Отправляет обе очереди — и не даёт одной уронить другую.
     *
     * Раньше здесь был живой дефект: [syncAttempts] после пометки неудачи бросал исключение, и до
     * [syncRatings] управление не доходило вовсе. Одна устойчиво отвергаемая попытка урока
     * навсегда останавливала отправку всех оценок квестов. Теперь каждая очередь идёт своим
     * заходом, а наружу отдаётся первая неудача — уже после того, как обе попробовали (AD-22).
     */
    override suspend fun sync(): Result<Unit> {
        val attempts = runStep { syncAttempts() }
        val ratings = runStep { syncRatings() }
        return attempts.exceptionOrNull()?.let { Result.failure(it) }
            ?: ratings.exceptionOrNull()?.let { Result.failure(it) }
            ?: Result.success(Unit)
    }

    private suspend fun runStep(block: suspend () -> Unit): Result<Unit> =
        try {
            block()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    private suspend fun syncAttempts() {
        val pending = outboxDao.pendingAttempts(batchSize)
        if (pending.isEmpty()) return
        val ids = pending.map { it.attemptId }
        try {
            remote.submitAttempts(pending.map { it.toRemoteEvent(answersFor(it.attemptId)) })
            outboxDao.markAttemptsSent(ids, nowMs())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            outboxDao.markAttemptsFailed(ids, e.syncError())
            throw e // наверх ловит runStep: соседняя очередь всё равно пойдёт
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
            throw e // наверх ловит runStep
        }
    }

    /** Answers live in their own table, written with the attempt; read them back at send time. */
    private suspend fun answersFor(attemptId: String): List<LessonAnswerEvent> =
        answerDao?.findByAttempt(attemptId).orEmpty().map { row ->
            LessonAnswerEvent(
                questionId = row.questionId,
                codeAnswerIndex = row.codeAnswerIndex,
                score = row.score,
                answerPayload = row.answerPayload,
                answeredAtMs = row.answeredAtMs,
                durationMs = row.durationMs,
                wasTimeout = row.wasTimeout == 1,
            )
        }

    private fun LessonResultAttemptOutboxEntity.toRemoteEvent(
        answers: List<LessonAnswerEvent>,
    ): LessonResultAttemptEvent =
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
            answers = answers,
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
