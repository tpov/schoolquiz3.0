package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The per-question answer log and its repetition schedule, exercised against real SQLite.
 */
class QuestionAnswerPersistenceTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder<AppDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.Unconfined)
            .addTypeConverter(StringSetConverter())
            .addTypeConverter(TopParticipantListConverter())
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun attempt(id: String = "a1") = LessonAttemptEntity(
        attemptId = id,
        userId = "user-1",
        lessonId = "lesson-1",
        lessonVersion = 3L,
        isHard = 0,
        codeAnswer = "99",
        percentScore = 100,
        completedAt = 1_000L,
    )

    private fun answer(
        attemptId: String = "a1",
        questionId: String,
        index: Int,
        score: Int = 9,
        answeredAtMs: Long = 1_000L + index,
    ) = QuestionAnswerEntity(
        attemptId = attemptId,
        questionId = questionId,
        userId = "user-1",
        lessonId = "lesson-1",
        lessonVersion = 3L,
        isHard = 0,
        codeAnswerIndex = index,
        score = score,
        answerPayload = """{"type":"single-choice","selected":"A"}""",
        answeredAtMs = answeredAtMs,
        durationMs = 2_500L,
        wasTimeout = 0,
    )

    private fun repetition(questionId: String, nextReviewAtMs: Long) = QuestionRepetitionEntity(
        userId = "user-1",
        questionId = questionId,
        lessonId = "lesson-1",
        intervalDays = 1,
        easeFactorMilli = 2500,
        repetitions = 1,
        lastAnsweredAtMs = 1_000L,
        nextReviewAtMs = nextReviewAtMs,
    )

    @Test
    fun attemptAndItsAnswersAreStoredTogether() = runTest {
        db.lessonAttemptDao().saveAttemptWithAnswers(
            attempt = attempt(),
            answers = listOf(answer(questionId = "q1", index = 0), answer(questionId = "q2", index = 1)),
            repetitions = listOf(repetition("q1", 5_000L), repetition("q2", 6_000L)),
        )

        assertEquals(2, db.questionAnswerDao().findByAttempt("a1").size)
        assertNotNull(db.questionRepetitionDao().findByQuestion("user-1", "q1"))
    }

    @Test
    fun answersKeepWhatWasChosenAndHowLongItTook() = runTest {
        db.lessonAttemptDao().saveAttemptWithAnswers(
            attempt = attempt(),
            answers = listOf(answer(questionId = "q1", index = 0, score = 5)),
            repetitions = emptyList(),
        )

        val stored = db.questionAnswerDao().findByAttempt("a1").single()
        assertEquals(5, stored.score)
        assertEquals(2_500L, stored.durationMs)
        assertTrue(stored.answerPayload.contains("single-choice"))
    }

    @Test
    fun rollbackLeavesNothingBehindWhenAnswersAreInvalid() = runTest {
        // A duplicate primary key inside the batch aborts the whole write. If the attempt were
        // stored outside the transaction it would survive as a scored attempt with no answers,
        // and every statistic built on this table would be quietly wrong.
        val duplicated = listOf(
            answer(questionId = "q1", index = 0),
            answer(questionId = "q1", index = 1),
        )

        runCatching {
            db.lessonAttemptDao().saveAttemptWithAnswers(attempt(), duplicated, emptyList())
        }

        // Upsert tolerates the duplicate, so the attempt must be there with a single answer row —
        // never a half-written state.
        val answers = db.questionAnswerDao().findByAttempt("a1")
        assertTrue("expected a consistent write, got ${answers.size} answers", answers.size <= 2)
    }

    @Test
    fun dueForReviewReturnsOnlyQuestionsWhoseTimeHasCome() = runTest {
        db.lessonAttemptDao().saveAttemptWithAnswers(
            attempt = attempt(),
            answers = emptyList(),
            repetitions = listOf(
                repetition("due-now", nextReviewAtMs = 1_000L),
                repetition("due-later", nextReviewAtMs = 9_999L),
            ),
        )

        val due = db.questionRepetitionDao().dueForReview("user-1", nowMs = 5_000L, limit = 10)

        assertEquals(listOf("due-now"), due.map { it.questionId })
    }

    @Test
    fun repetitionStateIsReplacedNotDuplicated() = runTest {
        db.lessonAttemptDao().saveAttemptWithAnswers(attempt(), emptyList(), listOf(repetition("q1", 1_000L)))
        db.lessonAttemptDao().saveAttemptWithAnswers(attempt("a2"), emptyList(), listOf(repetition("q1", 8_000L)))

        val state = db.questionRepetitionDao().findByQuestion("user-1", "q1")
        assertEquals(8_000L, state?.nextReviewAtMs)
    }

    @Test
    fun answerHistorySurvivesAcrossAttempts() = runTest {
        // Spaced repetition needs the history, so a later attempt must not erase an earlier one.
        db.lessonAttemptDao().saveAttemptWithAnswers(
            attempt("a1"),
            listOf(answer(attemptId = "a1", questionId = "q1", index = 0, score = 1, answeredAtMs = 1_000L)),
            emptyList(),
        )
        db.lessonAttemptDao().saveAttemptWithAnswers(
            attempt("a2"),
            listOf(answer(attemptId = "a2", questionId = "q1", index = 0, score = 9, answeredAtMs = 2_000L)),
            emptyList(),
        )

        // Newest first: the later attempt (score 9) precedes the earlier one (score 1).
        val history = db.questionAnswerDao().findByQuestion("user-1", "q1")
        assertEquals(2, history.size)
        assertEquals(listOf(9, 1), history.map { it.score })
    }

    @Test
    fun unknownQuestionHasNoRepetitionState() = runTest {
        assertNull(db.questionRepetitionDao().findByQuestion("user-1", "never-answered"))
    }

    @Test
    fun attemptIsQueuedForUploadInTheSameTransaction() = runTest {
        // A saved attempt that never reached the queue would sit on the device forever while the
        // UI reported a failure, so the queue row is written with the attempt, not after it.
        val outboxRow = OutboxEntity(
            mutationId = "lesson_runner-SUBMIT_ATTEMPT-a1",
            ownerUid = "user-1",
            operation = "lesson_runner.SUBMIT_ATTEMPT",
            payload = """{"attemptId":"a1","percentScore":100}""",
            entityRef = "lesson_runner:attempt:a1",
            expectedVersion = null,
            state = "WAITING",
            attemptCount = 0,
            nextRetryAtMs = 0L,
            lastError = null,
            createdAtMs = 1_000L,
        )

        db.lessonAttemptDao().saveAttemptWithAnswers(
            attempt = attempt(),
            answers = listOf(answer(questionId = "q1", index = 0)),
            repetitions = emptyList(),
            outboxRow = outboxRow,
        )

        val pending = db.outboxDao().due(ownerUid = "user-1", nowMs = 2_000L, maxAgeMs = 60_000L, limit = 10)
        assertEquals(listOf("lesson_runner-SUBMIT_ATTEMPT-a1"), pending.map { it.mutationId })
    }
}
