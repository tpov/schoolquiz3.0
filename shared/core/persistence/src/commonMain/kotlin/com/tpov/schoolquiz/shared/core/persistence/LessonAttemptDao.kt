package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.tpov.schoolquiz.shared.core.outbox.OutboxOperations
import kotlinx.coroutines.flow.Flow

@Dao
interface LessonAttemptDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LessonAttemptEntity): Long

    @Upsert
    suspend fun upsertAnswers(entities: List<QuestionAnswerEntity>)

    @Upsert
    suspend fun upsertRepetitions(entities: List<QuestionRepetitionEntity>)

    /**
     * Ставит запись в общую очередь (AD-5).
     *
     * `IGNORE`, а не `REPLACE`: ключ идемпотентности один на одно действие, и повторная постановка
     * того же намерения не должна создавать вторую операцию с тем же смыслом (AD-2). `REPLACE` к
     * тому же снёс бы строку вместе с её накопленными попытками и выдал новый `id`.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueueOutboxRow(entity: OutboxEntity): Long

    /** Состояние записи под этим ключом — чтобы отличить «уже отложено» от «уже не уедет». */
    @Query("SELECT state FROM outbox WHERE mutation_id = :mutationId")
    suspend fun outboxRowState(mutationId: String): String?

    @Query("SELECT * FROM lesson_attempts WHERE attempt_id = :attemptId")
    suspend fun findAttempt(attemptId: String): LessonAttemptEntity?

    @Query("SELECT * FROM question_answers WHERE attempt_id = :attemptId ORDER BY question_id")
    suspend fun findAttemptAnswers(attemptId: String): List<QuestionAnswerEntity>

    @Query("SELECT * FROM lesson_attempt_intents WHERE attempt_id = :attemptId")
    suspend fun findAttemptIntent(attemptId: String): LessonAttemptIntentEntity?

    @Query("SELECT * FROM outbox WHERE mutation_id = :mutationId")
    suspend fun findQueuedAttemptIntent(mutationId: String): OutboxEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAttemptIntent(entity: LessonAttemptIntentEntity): Long

    /** Persists an immutable unscored terminal request and its outbox row, not a scored result. */
    @Transaction
    suspend fun saveUnscoredAttemptIntent(
        attemptId: String,
        ownerUid: String,
        outboxRow: OutboxEntity,
    ) {
        // Raw answers are already frozen in payload. Never create a successful attempt or a
        // QuestionAnswerEntity with a made-up numeric score just to fit the old schema.
        validateUnscoredAttemptIdentity(attemptId, ownerUid, outboxRow)
        val previous = findAttempt(attemptId)
        val previousAnswers = findAttemptAnswers(attemptId)
        val marker = findAttemptIntent(attemptId)
        val queued = findQueuedAttemptIntent(outboxRow.mutationId)
        requireUnscoredAttempt(previous, previousAnswers)

        val incoming = outboxRow.attemptIntent(attemptId)
        if (marker != null) {
            requireSameAttemptIntent(marker, incoming)
            requireMatchingQueuedAttempt(queued, incoming)
            // Marker survives ACK. Identical retry must not re-enqueue, rewrite or reset backoff.
            return
        }
        // A queued-only legacy body is not evidence of this new raw-only persistence protocol.
        if (queued != null || outboxRow.state != "WAITING") throw AttemptIntentConflict()
        insertRequiredAttemptIntent(incoming)
        if (enqueueOutboxRow(outboxRow) == OUTBOX_ROW_IGNORED) throw AttemptIntentConflict()
    }

    /**
     * Stores a finished attempt: the attempt itself, the answers behind it, the repetition
     * schedule those answers move, and the row that will carry it all to the server.
     *
     * One transaction on purpose. Two failure modes used to be possible and both were silent:
     * a scored attempt with no answers would skew every statistic derived from them, and an
     * attempt saved but never queued would stay on the device forever while the UI reported a
     * save failure.
     *
     * Подавленная вставка не принимается на веру: −1 от `IGNORE` значит либо «то же намерение уже
     * отложено», либо «ключ занят записью, которая уже не уедет» — см. [requireOutboxIntentQueued].
     */
    @Transaction
    suspend fun saveAttemptWithAnswers(
        attempt: LessonAttemptEntity,
        answers: List<QuestionAnswerEntity>,
        repetitions: List<QuestionRepetitionEntity>,
        outboxRow: OutboxEntity? = null,
    ) {
        // The first terminal body is immutable across process death, transport retry and ACK.
        // The response receipt cannot substitute for it: a receipt does not contain the request.
        val previous = findAttempt(attempt.attemptId)
        val marker = findAttemptIntent(attempt.attemptId)
        val queued = outboxRow?.let { findQueuedAttemptIntent(it.mutationId) }
        if (outboxRow != null && outboxRow.ownerUid != attempt.userId) throw AttemptIntentConflict()
        if (previous != null || marker != null || queued != null) {
            requireUnchangedScoredAttempt(previous, attempt, answers)
            if (outboxRow == null) return // Legacy local-only retry never creates a server intent.
            val incoming = outboxRow.attemptIntent(attempt.attemptId)
            requireExistingAttemptProof(marker, queued, incoming)
            requireMatchingQueuedAttempt(queued, incoming)
            if (marker == null) insertRequiredAttemptIntent(incoming)
            return // Do not rewrite answers, repetition schedule or transport retry metadata.
        }
        outboxRow?.let {
            insertRequiredAttemptIntent(it.attemptIntent(attempt.attemptId))
        }
        upsert(attempt)
        if (answers.isNotEmpty()) upsertAnswers(answers)
        if (repetitions.isNotEmpty()) upsertRepetitions(repetitions)
        outboxRow?.let {
            val insertResult = enqueueOutboxRow(it)
            if (insertResult == OUTBOX_ROW_IGNORED) {
                requireOutboxIntentQueued(insertResult, it.mutationId, outboxRowState(it.mutationId))
            }
        }
    }

    /**
     * Убирает прохождение вместе с его ответами — откат по карантину (AD-28).
     *
     * Расписание повторений (`question_repetitions`) не трогается: его прежние значения перезаписаны
     * и восстановлению не подлежат, а само оно — локальная подсказка обучения, а не половина
     * серверной операции. Стереть его значило бы потерять больше, чем откатить.
     */
    @Transaction
    suspend fun rollbackAttempt(attemptId: String) {
        deleteAnswersOfAttempt(attemptId)
        deleteAttempt(attemptId)
    }

    @Query("DELETE FROM question_answers WHERE attempt_id = :attemptId")
    suspend fun deleteAnswersOfAttempt(attemptId: String)

    @Query("DELETE FROM lesson_attempts WHERE attempt_id = :attemptId")
    suspend fun deleteAttempt(attemptId: String)

    @Query("SELECT * FROM lesson_attempts WHERE user_id = :userId AND lesson_id = :lessonId")
    fun observeByLesson(
        userId: String,
        lessonId: String,
    ): Flow<List<LessonAttemptEntity>>

    @Query("SELECT * FROM lesson_attempts WHERE user_id = :userId")
    fun observeAllByUser(userId: String): Flow<List<LessonAttemptEntity>>

    /**
     * What this player earned and when, oldest first.
     *
     * Three columns, not whole rows: the activity chart adds up experience per day, and the answer
     * codes would come along for nothing.
     */
    @Query(
        "SELECT completed_at AS completedAt, percent_score AS percentScore, is_hard AS isHard " +
            "FROM lesson_attempts " +
            "WHERE user_id = :userId AND completed_at >= :sinceMs ORDER BY completed_at",
    )
    fun observeEarningsSince(
        userId: String,
        sinceMs: Long,
    ): Flow<List<LessonAttemptEarning>>
}

private fun validateUnscoredAttemptIdentity(
    attemptId: String,
    ownerUid: String,
    row: OutboxEntity,
) {
    if (attemptId.isBlank() || ownerUid.isBlank() || row.ownerUid != ownerUid) throw AttemptIntentConflict()
    if (row.operation != OutboxOperations.SUBMIT_ATTEMPT ||
        row.mutationId != OutboxOperations.mutationKey(OutboxOperations.SUBMIT_ATTEMPT, attemptId) ||
        row.entityRef != "lesson_runner:attempt:$attemptId"
    ) {
        throw AttemptIntentConflict()
    }
}

private fun requireUnscoredAttempt(
    previous: LessonAttemptEntity?,
    previousAnswers: List<QuestionAnswerEntity>,
) {
    if (previous != null || previousAnswers.isNotEmpty()) throw AttemptIntentConflict()
}

/** Runs within the caller's Room transaction; a changed attempt still short-circuits the answer read. */
private suspend fun LessonAttemptDao.requireUnchangedScoredAttempt(
    previous: LessonAttemptEntity?,
    attempt: LessonAttemptEntity,
    answers: List<QuestionAnswerEntity>,
) {
    if (previous != attempt || findAttemptAnswers(attempt.attemptId) != answers.sortedBy { it.questionId }) {
        throw AttemptIntentConflict()
    }
}

private fun requireExistingAttemptProof(
    marker: LessonAttemptIntentEntity?,
    queued: OutboxEntity?,
    incoming: LessonAttemptIntentEntity,
) {
    if (marker != null) {
        requireSameAttemptIntent(marker, incoming)
    } else if (queued?.attemptIntent(incoming.attemptId) != incoming) {
        // A legacy row without its original pending request is not completion proof.
        throw AttemptIntentConflict()
    }
}

private fun requireSameAttemptIntent(
    actual: LessonAttemptIntentEntity,
    incoming: LessonAttemptIntentEntity,
) {
    if (actual != incoming) throw AttemptIntentConflict()
}

private fun requireMatchingQueuedAttempt(
    queued: OutboxEntity?,
    incoming: LessonAttemptIntentEntity,
) {
    if (queued != null) {
        requireSameAttemptIntent(queued.attemptIntent(incoming.attemptId), incoming)
        requireOutboxIntentQueued(OUTBOX_ROW_IGNORED, queued.mutationId, queued.state)
    }
}

/** This insert remains part of the enclosing save transaction, before any attempt/answer writes. */
private suspend fun LessonAttemptDao.insertRequiredAttemptIntent(incoming: LessonAttemptIntentEntity) {
    if (insertAttemptIntent(incoming) == OUTBOX_ROW_IGNORED) throw AttemptIntentConflict()
}

/**
 * One finished attempt, reduced to what the activity chart needs.
 *
 * The experience is worked out here rather than stored, because the server computes it the same
 * way from the same two numbers — duplicating the figure would let the two drift apart.
 */
data class LessonAttemptEarning(
    val completedAt: Long,
    val percentScore: Int,
    val isHard: Int,
) {
    /** Mirrors `lessonResultReward` on the server: the score, doubled when the lesson was hard. */
    val experience: Int
        get() = percentScore.coerceIn(0, 100) * if (isHard != 0) HARD_LESSON_MULTIPLIER else 1

    private companion object {
        const val HARD_LESSON_MULTIPLIER = 2
    }
}
