package com.tpov.schoolquiz.shared.feature.lesson_runner.data

import com.tpov.schoolquiz.shared.core.outbox.OutboxOperations
import com.tpov.schoolquiz.shared.core.outbox.OutboxRecord
import com.tpov.schoolquiz.shared.core.outbox.OutboxState
import com.tpov.schoolquiz.shared.core.persistence.OutboxEntity
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.fake.FakeLessonAttemptDao
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.fake.FakeLessonRatingLocalDao
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.outbox.LessonResultEntityRef
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.outbox.LessonResultOutboxWriter
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.outbox.LessonResultQuarantineRollback
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.provider.DefaultRatingIdProvider
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.repository.LessonRatingRepositoryImpl
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.LessonRating
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.RatingId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Путь оценки целиком: поставил — карантин — откат — поставил заново.
 *
 * Раньше он обрывался на последнем шаге, и обрывался молча. Идентификатор оценки выводился из
 * пары «игрок и урок», поэтому ключ идемпотентности был у неё один навсегда; карантинная запись
 * из очереди не удаляется, откат снимал локальную отметку — и повторная оценка вставала под уже
 * занятый ключ, то есть не вставала. Игрок видел оценку поставленной, сервер не получал её
 * никогда.
 *
 * Провайдер здесь настоящий (`DefaultRatingIdProvider`) — проверяется именно то, что каждая
 * попытка оценить получает свой ключ.
 */
class RateAgainAfterQuarantineTest {

    private val ratingDao = FakeLessonRatingLocalDao()
    private val repository = LessonRatingRepositoryImpl(ratingDao, TestRatingOutboxWriter)
    private val rollback = LessonResultQuarantineRollback(FakeLessonAttemptDao(), ratingDao)
    private val ratingIdProvider = DefaultRatingIdProvider()

    @Test
    fun quarantinedRatingCanBeSubmittedAgainAndReachesTheQueue() = runTest {
        // Оценил.
        val first = rate()
        assertTrue("первая оценка обязана сохраниться", first.isSuccess)
        assertEquals(1, ratingDao.outboxRows.size)
        assertTrue(ratingDao.hasSubmitted(USER_ID, LESSON_ID).first())

        // Карантин: движок помечает запись и оставляет её лежать.
        val stuck = ratingDao.outboxRows.single()
        ratingDao.quarantine(stuck.mutationId)

        // Откат по карантину — реакция фичи на терминальную запись (AD-28).
        rollback.ratings.onQuarantined(quarantineRecord(stuck))
        assertFalse("после отката оценки локально быть не должно", ratingDao.hasSubmitted(USER_ID, LESSON_ID).first())

        // Оценил заново.
        val second = rate()

        assertTrue("повторная оценка обязана сохраниться", second.isSuccess)
        assertEquals("повторная оценка обязана встать в очередь", 2, ratingDao.outboxRows.size)
        val requeued = ratingDao.outboxRows.last()
        assertNotEquals("новое намерение — новый ключ", stuck.mutationId, requeued.mutationId)
        assertEquals(OutboxState.WAITING.name, requeued.state)
        assertTrue(ratingDao.hasSubmitted(USER_ID, LESSON_ID).first())
    }

    /** Одно намерение, отданное в очередь дважды, второй записи не создаёт (AD-2). */
    @Test
    fun theSameIntentOfferedTwiceQueuesOnce() = runTest {
        val rating = ratingOf(ratingIdProvider.provide(USER_ID, LessonId(LESSON_ID)).value)

        assertTrue(repository.submit(rating).isSuccess)
        assertTrue(repository.submit(rating).isSuccess)

        assertEquals(1, ratingDao.outboxRows.size)
        assertTrue(ratingDao.hasSubmitted(USER_ID, LESSON_ID).first())
    }

    private suspend fun rate() = repository.submit(ratingOf(ratingIdProvider.provide(USER_ID, LessonId(LESSON_ID)).value))

    private fun ratingOf(ratingId: String) =
        LessonRating(
            id = RatingId(ratingId),
            userId = USER_ID,
            lessonId = LessonId(LESSON_ID),
            lessonVersion = 1L,
            rating = 3,
            ratedAt = 1_000L,
        )

    private fun quarantineRecord(row: OutboxEntity) =
        OutboxRecord(
            id = 1L,
            mutationId = row.mutationId,
            ownerUid = row.ownerUid,
            operation = row.operation,
            payload = row.payload,
            entityRef = row.entityRef,
            state = OutboxState.QUARANTINED,
            createdAtMs = row.createdAtMs,
            attemptCount = 5,
            lastError = "Refused by server",
        )

    /**
     * Строка очереди без чтения предков урока.
     *
     * Ключ и ссылку собирают те же самые функции, что и настоящий писатель: расходиться им нельзя,
     * иначе тест доказывал бы поведение, которого в приложении нет.
     */
    private object TestRatingOutboxWriter : LessonResultOutboxWriter {
        override suspend fun buildRatingRow(rating: LessonRating): OutboxEntity =
            OutboxEntity(
                mutationId = OutboxOperations.mutationKey(OutboxOperations.SUBMIT_RATING, rating.id.value),
                ownerUid = rating.userId,
                operation = OutboxOperations.SUBMIT_RATING,
                payload = """{"ratingId":"${rating.id.value}","userId":"${rating.userId}",""" +
                    """"lessonId":"${rating.lessonId.value}","rating":${rating.rating}}""",
                entityRef = LessonResultEntityRef.rating(rating.id.value),
                expectedVersion = null,
                state = OutboxState.WAITING.name,
                attemptCount = 0,
                nextRetryAtMs = 0L,
                lastError = null,
                createdAtMs = rating.ratedAt,
            )
    }

    private companion object {
        const val USER_ID = "user-1"
        const val LESSON_ID = "lesson-1"
    }
}
