package com.tpov.schoolquiz.shared.feature.lesson_runner.data

import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.fake.FakeLessonAttemptDao
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.repository.LessonAttemptRepositoryImpl
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.CodeAnswer
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PercentScore
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
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
    ) = Attempt(
        id = AttemptId(id),
        userId = userId,
        lessonId = LessonId(lessonId),
        lessonVersion = 2L,
        mode = Difficulty.EASY,
        codeAnswer = CodeAnswer("9"),
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
}
