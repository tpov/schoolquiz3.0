package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.tpov.schoolquiz.shared.core.outbox.OutboxOperations
import com.tpov.schoolquiz.shared.core.outbox.OutboxState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Оценка после карантина — против настоящего SQLite.
 *
 * Всё, что тут проверяется, держится на поведении, которого фейком не изобразить честно:
 * уникальный индекс по `mutation_id`, `−1` от подавленной вставки и откат транзакции при
 * исключении внутри неё.
 *
 * История, ради которой тест написан: запись оценки ушла в карантин, движок её не удалил (он
 * удаляет только доехавшее), фича откатила локальную отметку, чтобы игрок оценил заново, — и
 * оценка обязана снова встать в очередь.
 */
class LessonRatingOutboxRequeueTest {

    private lateinit var db: AppDatabase
    private lateinit var ratings: LessonRatingLocalDao
    private lateinit var outbox: OutboxDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder<AppDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.Unconfined)
            .addTypeConverter(StringSetConverter())
            .addTypeConverter(TopParticipantListConverter())
            .build()
        ratings = db.lessonRatingLocalDao()
        outbox = db.outboxDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun ratingSubmittedAgainAfterQuarantineRollbackReachesTheQueue() = runTest {
        // Оценка поставлена: локальная отметка и строка очереди одной транзакцией.
        ratings.submitWithOutbox(mark(submittedAt = 1_000L), row("rating-1"))
        assertNotNull("первая оценка не встала в очередь", outbox.findByMutationId(key("rating-1")))

        // Карантин. Движок помечает запись и оставляет её лежать: удаляется только доехавшее.
        val quarantined = outbox.findByMutationId(key("rating-1"))!!
        outbox.applyDecision(
            id = quarantined.id,
            state = OutboxState.QUARANTINED.name,
            nextRetryAtMs = 0L,
            attemptCount = 5,
            lastError = "Refused by server",
        )

        // Откат по карантину: отметки «оценка поставлена» больше нет, игрок может оценить заново.
        ratings.delete(USER_ID, LESSON_ID)
        assertFalse(ratings.hasSubmitted(USER_ID, LESSON_ID).first())

        // Игрок оценивает заново. Это новое намерение — у него свой идентификатор и свой ключ.
        ratings.submitWithOutbox(mark(submittedAt = 2_000L), row("rating-2"))

        val requeued = outbox.findByMutationId(key("rating-2"))
        assertNotNull("повторная оценка обязана встать в очередь", requeued)
        assertEquals(OutboxState.WAITING.name, requeued!!.state)
        assertTrue(ratings.hasSubmitted(USER_ID, LESSON_ID).first())
        // Карантинная строка осталась собой: её попытки не обнулились.
        assertEquals(5, outbox.findByMutationId(key("rating-1"))!!.attemptCount)
    }

    @Test
    fun theSameIntentOfferedTwiceQueuesOnce() = runTest {
        ratings.submitWithOutbox(mark(submittedAt = 1_000L), row("rating-1"))
        // Та же оценка, тот же идентификатор: переигранная транзакция, а не второе намерение.
        ratings.submitWithOutbox(mark(submittedAt = 1_000L), row("rating-1"))

        assertEquals(1, outbox.countPending(USER_ID))
        assertTrue(ratings.hasSubmitted(USER_ID, LESSON_ID).first())
    }

    /**
     * Прежнее поведение, ставшее слышным.
     *
     * Пока идентификатор оценки выводился из пары «игрок и урок», повторная оценка приходила под
     * тем же ключом, натыкалась на карантинную строку и молча не вставала: `IGNORE` возвращал −1,
     * и никто его не смотрел. Теперь такой ключ роняет транзакцию целиком — вместе с локальной
     * отметкой, которая иначе осталась бы показывать оценку поставленной.
     */
    @Test
    fun aKeyHeldByAQuarantinedRowRefusesTheWholeTransaction() = runTest {
        ratings.submitWithOutbox(mark(submittedAt = 1_000L), row("rating-1"))
        val stuck = outbox.findByMutationId(key("rating-1"))!!
        outbox.applyDecision(stuck.id, OutboxState.QUARANTINED.name, 0L, 5, "Refused by server")
        ratings.delete(USER_ID, LESSON_ID)

        val failure = runCatching { ratings.submitWithOutbox(mark(submittedAt = 2_000L), row("rating-1")) }

        assertTrue("занятый терминальной записью ключ обязан быть слышен", failure.isFailure)
        assertFalse(
            "локальная отметка не должна пережить отказ: оценка не уедет, значит её нет",
            ratings.hasSubmitted(USER_ID, LESSON_ID).first(),
        )
    }

    private fun mark(submittedAt: Long) =
        LessonRatingSubmittedLocalEntity(userId = USER_ID, lessonId = LESSON_ID, submittedAt = submittedAt)

    private fun key(ratingId: String) = OutboxOperations.mutationKey(OutboxOperations.SUBMIT_RATING, ratingId)

    private fun row(ratingId: String) =
        OutboxEntity(
            mutationId = key(ratingId),
            ownerUid = USER_ID,
            operation = OutboxOperations.SUBMIT_RATING,
            payload = """{"ratingId":"$ratingId","userId":"$USER_ID","lessonId":"$LESSON_ID","rating":3}""",
            entityRef = "lesson_runner:rating:$ratingId",
            expectedVersion = null,
            state = OutboxState.WAITING.name,
            attemptCount = 0,
            nextRetryAtMs = 0L,
            lastError = null,
            createdAtMs = 1_000L,
        )

    private companion object {
        const val USER_ID = "user-1"
        const val LESSON_ID = "lesson-1"
    }
}
