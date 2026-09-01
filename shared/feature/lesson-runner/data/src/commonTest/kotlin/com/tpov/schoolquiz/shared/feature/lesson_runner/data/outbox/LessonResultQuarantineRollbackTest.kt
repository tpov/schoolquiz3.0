package com.tpov.schoolquiz.shared.feature.lesson_runner.data.outbox

import com.tpov.schoolquiz.shared.core.outbox.OutboxOperations
import com.tpov.schoolquiz.shared.core.outbox.OutboxRecord
import com.tpov.schoolquiz.shared.core.outbox.OutboxState
import com.tpov.schoolquiz.shared.core.persistence.LessonAttemptEntity
import com.tpov.schoolquiz.shared.core.persistence.LessonRatingSubmittedLocalEntity
import com.tpov.schoolquiz.shared.core.persistence.QuestionAnswerEntity
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.fake.FakeLessonAttemptDao
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.fake.FakeLessonRatingLocalDao
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Карантин терминален: запись больше не уедет никогда. Локальная половина, сделанная вместе с ней
 * одной транзакцией, обязана исчезнуть — иначе игрок навсегда остаётся с прохождением, которого на
 * сервере нет, и с оценкой, которую нельзя поставить снова (AD-28).
 */
class LessonResultQuarantineRollbackTest {

    @Test
    fun quarantinedAttempt_removesTheAttemptAndItsAnswers() = runTest {
        val attemptDao = FakeLessonAttemptDao()
        val ratingDao = FakeLessonRatingLocalDao()
        attemptDao.upsert(attemptRow())
        attemptDao.upsertAnswers(listOf(answerRow()))

        // До
        assertEquals(1, attemptDao.attempts.size)
        assertEquals(1, attemptDao.answers.size)

        LessonResultQuarantineRollback(attemptDao, ratingDao)
            .attempts
            .onQuarantined(record(OutboxOperations.SUBMIT_ATTEMPT, LessonResultEntityRef.attempt(ATTEMPT_ID)))

        // После
        assertTrue(attemptDao.attempts.isEmpty(), "прохождение должно исчезнуть")
        assertTrue(attemptDao.answers.isEmpty(), "ответы прохождения должны исчезнуть вместе с ним")
    }

    @Test
    fun quarantinedAttempt_leavesOtherAttemptsAlone() = runTest {
        val attemptDao = FakeLessonAttemptDao()
        attemptDao.upsert(attemptRow())
        attemptDao.upsert(attemptRow(attemptId = "attempt-2"))
        attemptDao.upsertAnswers(listOf(answerRow(), answerRow(attemptId = "attempt-2")))

        LessonResultQuarantineRollback(attemptDao, FakeLessonRatingLocalDao())
            .attempts
            .onQuarantined(record(OutboxOperations.SUBMIT_ATTEMPT, LessonResultEntityRef.attempt(ATTEMPT_ID)))

        assertEquals(listOf("attempt-2"), attemptDao.attempts.map { it.attemptId })
        assertEquals(listOf("attempt-2"), attemptDao.answers.map { it.attemptId })
    }

    @Test
    fun quarantinedRating_letsThePlayerRateAgain() = runTest {
        val ratingDao = FakeLessonRatingLocalDao()
        ratingDao.upsert(LessonRatingSubmittedLocalEntity(USER_ID, LESSON_ID, submittedAt = 5L))
        ratingDao.upsert(LessonRatingSubmittedLocalEntity(USER_ID, "lesson-other", submittedAt = 5L))

        // До
        assertTrue(ratingDao.rows.contains(USER_ID to LESSON_ID))

        LessonResultQuarantineRollback(FakeLessonAttemptDao(), ratingDao)
            .ratings
            .onQuarantined(record(OutboxOperations.SUBMIT_RATING, LessonResultEntityRef.rating(RATING_ID), RATING_BODY))

        // После: пары «игрок и урок» больше нет, а соседний урок не тронут.
        assertEquals(setOf(USER_ID to "lesson-other"), ratingDao.rows)
    }

    /**
     * Ссылка перенесённой миграцией строки называет `ratingId`, которого в локальной таблице нет.
     * Откат обязан справляться и с ней — иначе оценки, пережившие переезд, остаются запертыми.
     */
    @Test
    fun quarantinedRating_readsThePairFromTheBodyWhenTheRefNamesOnlyTheRatingId() = runTest {
        val ratingDao = FakeLessonRatingLocalDao()
        ratingDao.upsert(LessonRatingSubmittedLocalEntity(USER_ID, LESSON_ID, submittedAt = 5L))

        LessonResultQuarantineRollback(FakeLessonAttemptDao(), ratingDao)
            .ratings
            .onQuarantined(record(OutboxOperations.SUBMIT_RATING, "lesson_runner:rating:$RATING_ID", RATING_BODY))

        assertTrue(ratingDao.rows.isEmpty())
    }

    @Test
    fun aBodyWithoutALesson_changesNothing() = runTest {
        val ratingDao = FakeLessonRatingLocalDao()
        ratingDao.upsert(LessonRatingSubmittedLocalEntity(USER_ID, LESSON_ID, submittedAt = 5L))

        LessonResultQuarantineRollback(FakeLessonAttemptDao(), ratingDao)
            .ratings
            .onQuarantined(record(OutboxOperations.SUBMIT_RATING, null, "{}"))

        assertEquals(setOf(USER_ID to LESSON_ID), ratingDao.rows)
    }

    private fun record(
        operation: String,
        entityRef: String?,
        payload: String = "{}",
    ) = OutboxRecord(
        id = 1L,
        mutationId = OutboxOperations.mutationKey(operation, "source-1"),
        ownerUid = USER_ID,
        operation = operation,
        payload = payload,
        entityRef = entityRef,
        state = OutboxState.QUARANTINED,
        createdAtMs = 1L,
        attemptCount = 5,
        lastError = "Refused by server",
    )

    private fun attemptRow(attemptId: String = ATTEMPT_ID) =
        LessonAttemptEntity(
            attemptId = attemptId,
            userId = USER_ID,
            lessonId = LESSON_ID,
            lessonVersion = 1L,
            isHard = 0,
            codeAnswer = "99",
            percentScore = 100,
            completedAt = 10L,
        )

    private fun answerRow(attemptId: String = ATTEMPT_ID) =
        QuestionAnswerEntity(
            attemptId = attemptId,
            questionId = "question-1",
            userId = USER_ID,
            lessonId = LESSON_ID,
            lessonVersion = 1L,
            isHard = 0,
            codeAnswerIndex = 0,
            score = 9,
            answerPayload = "{}",
            answeredAtMs = 9L,
            durationMs = 1L,
            wasTimeout = 0,
        )

    private companion object {
        const val USER_ID = "user-1"
        const val LESSON_ID = "lesson-1"
        const val ATTEMPT_ID = "attempt-1"
        const val RATING_ID = "rating-1"
        const val RATING_BODY = """{"ratingId":"rating-1","userId":"user-1","lessonId":"lesson-1","rating":3}"""
    }
}
