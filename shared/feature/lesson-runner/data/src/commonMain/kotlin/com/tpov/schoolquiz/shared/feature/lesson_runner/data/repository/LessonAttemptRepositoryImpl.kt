package com.tpov.schoolquiz.shared.feature.lesson_runner.data.repository

import com.tpov.schoolquiz.shared.core.scoring.ChargeClaimMask
import com.tpov.schoolquiz.shared.core.persistence.LessonAttemptDao
import com.tpov.schoolquiz.shared.core.persistence.QuestionRepetitionDao
import com.tpov.schoolquiz.shared.core.persistence.QuestionRepetitionEntity
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.mapper.toDomain
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.mapper.toEntity
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.outbox.LessonResultOutboxWriter
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.SpacedRepetition
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AnsweredQuestion
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ServedQuestion
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.LessonAttemptRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LessonAttemptRepositoryImpl(
    private val attemptDao: LessonAttemptDao,
    private val outboxWriter: LessonResultOutboxWriter = LessonResultOutboxWriter.NoOp,
    private val repetitionDao: QuestionRepetitionDao? = null,
) : LessonAttemptRepository {

    override suspend fun save(attempt: Attempt): Result<Unit> =
        persist(attempt, emptyList(), served = null, claims = null)

    override suspend fun save(attempt: Attempt, answers: List<AnsweredQuestion>): Result<Unit> =
        persist(attempt, answers, served = null, claims = null)

    override suspend fun save(
        attempt: Attempt,
        answers: List<AnsweredQuestion>,
        served: List<ServedQuestion>,
    ): Result<Unit> = persist(attempt, answers, served, claims = null)

    override suspend fun save(
        attempt: Attempt,
        answers: List<AnsweredQuestion>,
        served: List<ServedQuestion>,
        claims: ChargeClaimMask?,
    ): Result<Unit> = persist(attempt, answers, served, claims)

    /**
     * [served] is null only through the two fake-only conveniences above: they queue a body without
     * the key, which the server reads as "unknown" rather than as "nothing was shown".
     */
    private suspend fun persist(
        attempt: Attempt,
        answers: List<AnsweredQuestion>,
        served: List<ServedQuestion>?,
        claims: ChargeClaimMask?,
    ): Result<Unit> = runCatching {
        val answerEntities = answers.map { it.toEntity(attempt) }
        val repetitionEntities = buildRepetitionStates(attempt, answers)
        // Resolved before the transaction because it reads the lesson's ancestors; the row itself
        // is written inside it, so the attempt can never be saved without being queued. The served
        // list goes only into that row: the attempt row keeps its shape.
        val outboxRow = outboxWriter.buildAttemptRow(attempt, answerEntities, served, claims)

        attemptDao.saveAttemptWithAnswers(
            attempt = attempt.toEntity(),
            answers = answerEntities,
            repetitions = repetitionEntities,
            outboxRow = outboxRow,
        )
    }

    /**
     * Advances the repetition schedule for every answered question.
     *
     * Reads the previous state first: SM-2 needs the current interval and ease to decide the next
     * showing, and a question answered for the first time simply has none.
     */
    private suspend fun buildRepetitionStates(
        attempt: Attempt,
        answers: List<AnsweredQuestion>,
    ): List<QuestionRepetitionEntity> {
        val dao = repetitionDao ?: return emptyList()
        if (answers.isEmpty()) return emptyList()

        val previous = dao
            .findByQuestions(attempt.userId, answers.map { it.questionId.value })
            .associateBy { it.questionId }

        return answers.map { answered ->
            val next = SpacedRepetition.next(
                previous = previous[answered.questionId.value]?.toDomain(),
                score = answered.score,
                answeredAtMs = answered.answeredAtMs,
            )
            next.toEntity(
                userId = attempt.userId,
                questionId = answered.questionId.value,
                lessonId = attempt.lessonId.value,
            )
        }
    }

    override fun observeByLesson(userId: String, lessonId: LessonId): Flow<List<Attempt>> =
        attemptDao.observeByLesson(userId, lessonId.value)
            .map { list -> list.map { it.toDomain() } }

    override fun observeAllByUser(userId: String): Flow<List<Attempt>> =
        attemptDao.observeAllByUser(userId)
            .map { list -> list.map { it.toDomain() } }
}
