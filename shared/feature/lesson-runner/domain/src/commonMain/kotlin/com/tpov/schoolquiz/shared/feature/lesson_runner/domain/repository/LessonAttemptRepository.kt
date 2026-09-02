package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository

import com.tpov.schoolquiz.shared.core.scoring.ChargeClaimMask
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computeBestStars
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computeHardUnlocked
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AnsweredQuestion
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ServedQuestion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Derived per-lesson progress used by lesson-list consumers (ADR-QS-16). */
data class LessonAttemptStats(
    val bestStarsRawTenths: Int,
    val hardUnlocked: Boolean,
)

interface LessonAttemptRepository {

    /**
     * Persists [attempt] to Room.
     * Returns [Result.failure] on IO error; caller transitions to SaveFailed state.
     *
     * Fake-only convenience: the one form every fake overrides. Production callers must use the
     * three-argument [save] — a body queued through this one carries no served list, which the
     * server will read as "unknown" once the wiring step looks for it.
     */
    suspend fun save(attempt: Attempt): Result<Unit>

    /**
     * Persists [attempt] together with the [answers] it is made of, in one transaction.
     *
     * The attempt alone keeps a digit per question; the answers carry what was actually chosen,
     * how long it took and whether the timer stepped in — the data spaced repetition, lesson
     * statistics and survey distributions are built from.
     *
     * Fake-only convenience, like the one-argument form: production callers must use the
     * three-argument [save]. Defaults to [save] so fakes that only care about attempts keep working.
     */
    suspend fun save(attempt: Attempt, answers: List<AnsweredQuestion>): Result<Unit> = save(attempt)

    /**
     * Persists [attempt], its [answers] and the questions it [served], in one transaction.
     *
     * This is the form production callers use. [served] is the attempt's whole play order — every
     * question dealt into it, counted as shown whether or not the player reached it, per
     * `buildCodeAnswerOnAbort`. The server reads it: every digit of a hard attempt it scores itself
     * is placed from this list, and on an attempt this device scored the list is checked against the
     * digits — one that disagrees is stored, marked and paid nothing. Only the queued body carries
     * it; the attempt row does not.
     *
     * Defaults to [save] with answers so fakes that only care about attempts keep working.
     */
    suspend fun save(
        attempt: Attempt,
        answers: List<AnsweredQuestion>,
        served: List<ServedQuestion>,
    ): Result<Unit> = save(attempt, answers)

    /**
     * То же плюс [claims] — заявки на заряды по позициям `codeAnswer`.
     *
     * Заявка едет только в строку очереди, в той же транзакции, что и сама попытка: иначе перезапуск
     * между сохранением и отправкой стирал бы её, и подсказка оказывалась бы бесплатной. В таблице
     * попыток заявке места не нужно — локально её никто не читает, платит по ней сервер.
     */
    suspend fun save(
        attempt: Attempt,
        answers: List<AnsweredQuestion>,
        served: List<ServedQuestion>,
        claims: ChargeClaimMask?,
    ): Result<Unit> = save(attempt, answers, served)

    /**
     * Observes all attempts for [userId] + [lessonId], sorted by completedAt DESC.
     */
    fun observeByLesson(userId: String, lessonId: LessonId): Flow<List<Attempt>>

    /**
     * Observes all attempts for [userId] across all lessons.
     */
    fun observeAllByUser(userId: String): Flow<List<Attempt>>

    /**
     * Derived view: maps all user attempts to per-lessonId stats (bestStars + hardUnlocked).
     * Default implementation delegates to [observeAllByUser]; override for performance if needed.
     */
    fun observeAllStatsByUser(userId: String): Flow<Map<LessonId, LessonAttemptStats>> =
        observeAllByUser(userId).map { attempts ->
            attempts.groupBy { it.lessonId }.mapValues { (_, lessonAttempts) ->
                LessonAttemptStats(
                    bestStarsRawTenths = computeBestStars(lessonAttempts).rawTenths,
                    hardUnlocked = computeHardUnlocked(lessonAttempts),
                )
            }
        }
}
