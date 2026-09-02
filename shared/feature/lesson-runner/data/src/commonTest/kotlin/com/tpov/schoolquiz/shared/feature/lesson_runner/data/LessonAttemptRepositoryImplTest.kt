package com.tpov.schoolquiz.shared.feature.lesson_runner.data

import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.core.question_schema.OptionId
import com.tpov.schoolquiz.shared.core.scoring.CodeAnswer
import com.tpov.schoolquiz.shared.core.scoring.PercentScore
import com.tpov.schoolquiz.shared.core.scoring.Score
import com.tpov.schoolquiz.shared.core.scoring.UserAnswer
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.fake.FakeContentHierarchy
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.fake.FakeLessonAttemptDao
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.fake.FakeLessonResultOutboxWriter
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.repository.LessonAttemptRepositoryImpl
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AnsweredQuestion
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ServedQuestion
import com.tpov.schoolquiz.shared.feature.question.domain.model.QuestionId
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Repository proxy tests for LessonAttemptRepositoryImpl using FakeLessonAttemptDao.
 * Source: docs/features/lesson-runner/plan/phase-03/tests.md §IT-01, §IT-07
 */
class LessonAttemptRepositoryImplTest {

    private val fakeDao = FakeLessonAttemptDao()
    private val repo = LessonAttemptRepositoryImpl(fakeDao)

    private fun makeAttempt(
        id: String = "a1",
        userId: String = "u1",
        lessonId: String = "l1",
        codeAnswer: String = "9",
    ) = Attempt(
        id = AttemptId(id),
        userId = userId,
        lessonId = LessonId(lessonId),
        lessonVersion = 2L,
        mode = Difficulty.EASY,
        codeAnswer = CodeAnswer(codeAnswer),
        percentScore = PercentScore(100),
        completedAt = 1000L,
    )

    // IT-01: GIVEN LessonAttemptRepositoryImpl + FakeLessonAttemptDao
    //        WHEN save(attempt) THEN Result.success, dao.upsert called once
    //        AND observeByLesson returns attempt with matching fields
    @Test
    fun lessonAttemptRepository_save_thenObserve() = runTest {
        val attempt = makeAttempt()

        val result = repo.save(attempt)

        assertTrue(result.isSuccess, "save() must return Result.success")
        assertEquals(1, fakeDao.upsertCallCount, "dao.upsert must be called exactly once")

        val observed = repo.observeByLesson("u1", LessonId("l1")).take(1).toList()
        assertTrue(observed.isNotEmpty())
        val items = observed.first()
        assertEquals(1, items.size, "observed list must contain 1 attempt")
        assertEquals(attempt.id, items.first().id)
        assertEquals(attempt.userId, items.first().userId)
        assertEquals(attempt.lessonId, items.first().lessonId)
    }

    // IT-07: GIVEN FakeLessonAttemptDao with upsertCallCount=0
    //        WHEN 3 questions answered WITHOUT calling save()
    //        THEN upsertCallCount == 0 (only repository.save() triggers dao.upsert)
    @Test
    fun lessonAttemptRepository_noWritesDuringPlaythrough() {
        // Simulate 3 questions answered in-memory — no save() called on repo
        assertEquals(0, fakeDao.upsertCallCount, "No dao.upsert calls should happen without save()")
    }

    // The served list travels only into the queued body: the rows the attempt is made of are
    // byte-for-byte what a save without it writes.
    @Test
    fun lessonAttemptRepository_servedReachesTheWriterAndNothingElse() = runTest {
        val attempt = makeAttempt(codeAnswer = "909")
        val answers = listOf(answered("q-0", 0), answered("q-2", 2))
        val served = listOf(ServedQuestion(QuestionId("q-0"), 0), ServedQuestion(QuestionId("q-2"), 2))

        val withoutDao = FakeLessonAttemptDao()
        val withoutWriter = FakeLessonResultOutboxWriter()
        LessonAttemptRepositoryImpl(withoutDao, withoutWriter).save(attempt, answers)

        val withDao = FakeLessonAttemptDao()
        val withWriter = FakeLessonResultOutboxWriter()
        val result = LessonAttemptRepositoryImpl(withDao, withWriter).save(attempt, answers, served)

        assertTrue(result.isSuccess)
        assertEquals(withoutDao.attempts, withDao.attempts, "attempt row must not change")
        assertEquals(withoutDao.answers, withDao.answers, "answer rows must not change")
        assertEquals(served, withWriter.lastAttemptCall.served, "served must reach the writer as given")
        assertNull(withoutWriter.lastAttemptCall.served, "the two-argument form supplies no list: unknown, not none")
    }

    // The only test that drives the production wiring end to end: repository → Room writer → the
    // DAO transaction that holds the attempt, its answers and the queued body together.
    @Test
    fun lessonAttemptRepository_queuesServedInTheSameTransactionAsTheAttempt() = runTest {
        val dao = FakeLessonAttemptDao()
        val repo = LessonAttemptRepositoryImpl(dao, FakeContentHierarchy.roomWriter())
        val attempt = makeAttempt(lessonId = FakeContentHierarchy.LESSON_ID, codeAnswer = "909")
        val answers = listOf(answered("q-0", 0), answered("q-2", 2))
        val served = listOf(ServedQuestion(QuestionId("q-0"), 0), ServedQuestion(QuestionId("q-2"), 2))

        val result = repo.save(attempt, answers, served)

        assertTrue(result.isSuccess)
        assertEquals(listOf(attempt.id.value), dao.attempts.map { it.attemptId })
        assertEquals(listOf("q-0", "q-2"), dao.answers.map { it.questionId })
        val payload = Json.parseToJsonElement(dao.outboxRows.single().payload).jsonObject
        assertEquals(attempt.id.value, payload.getValue("attemptId").jsonPrimitive.content)
        assertEquals(
            listOf("q-0" to 0, "q-2" to 2),
            payload.getValue("served").jsonArray.map { entry ->
                entry.jsonObject.getValue("questionId").jsonPrimitive.content to
                    entry.jsonObject.getValue("codeAnswerIndex").jsonPrimitive.int
            },
        )
    }

    // A body the writer refuses never reaches the transaction: the device sees SaveFailed now, not
    // a quarantined row weeks later — and nothing is left half-written.
    @Test
    fun lessonAttemptRepository_refusedBodyLeavesNothingBehind() = runTest {
        val dao = FakeLessonAttemptDao()
        val repo = LessonAttemptRepositoryImpl(dao, FakeContentHierarchy.roomWriter())
        val attempt = makeAttempt(lessonId = FakeContentHierarchy.LESSON_ID, codeAnswer = "909")
        val answers = listOf(answered("q-0", 0), answered("q-2", 2))
        val servedMissingOne = listOf(ServedQuestion(QuestionId("q-0"), 0))

        val result = repo.save(attempt, answers, servedMissingOne)

        assertTrue(result.isFailure)
        assertIs<IllegalArgumentException>(result.exceptionOrNull())
        assertEquals(0, dao.upsertCallCount)
        assertTrue(dao.attempts.isEmpty())
        assertTrue(dao.answers.isEmpty())
        assertTrue(dao.outboxRows.isEmpty())
    }

    private fun answered(questionId: String, codeAnswerIndex: Int) = AnsweredQuestion(
        questionId = QuestionId(questionId),
        codeAnswerIndex = codeAnswerIndex,
        score = Score(9),
        answer = UserAnswer.SingleChoiceAnswer(OptionId("A")),
        answeredAtMs = 1L,
        durationMs = 1L,
        wasTimeout = false,
    )
}
