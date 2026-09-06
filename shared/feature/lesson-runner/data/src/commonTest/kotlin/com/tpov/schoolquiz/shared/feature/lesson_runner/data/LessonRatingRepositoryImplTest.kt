package com.tpov.schoolquiz.shared.feature.lesson_runner.data

import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.fake.FakeContentHierarchy
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.fake.FakeLessonRatingLocalDao
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.repository.LessonRatingRepositoryImpl
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.LessonRating
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.RatingId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Repository proxy tests for LessonRatingRepositoryImpl using FakeLessonRatingLocalDao.
 * Source: docs/features/lesson-runner/plan/phase-03/tests.md §IT-08, §IT-08b
 */
class LessonRatingRepositoryImplTest {

    private val fakeDao = FakeLessonRatingLocalDao()
    private val repo = LessonRatingRepositoryImpl(fakeDao)

    private fun makeRating(
        userId: String = "u1",
        lessonId: String = "l1",
    ) = LessonRating(
        id = RatingId("rating-$userId-$lessonId"),
        userId = userId,
        lessonId = LessonId(lessonId),
        lessonVersion = 1L,
        rating = 2,
        ratedAt = 1000L,
    )

    // IT-08: GIVEN LessonRatingRepositoryImpl + FakeLessonRatingLocalDao
    //        WHEN submit(rating for u1/l1)
    //        THEN Result.success AND hasSubmitted("u1", LessonId("l1")) returns true
    @Test
    fun lessonRatingRepository_submit_thenHasSubmitted() = runTest {
        val rating = makeRating(userId = "u1", lessonId = "l1")

        val result = repo.submit(rating)

        assertTrue(result.isSuccess, "submit() must return Result.success")
        val hasSubmitted = repo.hasSubmitted("u1", LessonId("l1")).take(1).first()
        assertTrue(hasSubmitted, "hasSubmitted must be true after submit")
    }

    // IT-08b: GIVEN submit for (u1, l1)
    //         WHEN hasSubmitted("u1", LessonId("l2"))
    //         THEN false (different lessonId)
    @Test
    fun lessonRatingRepository_hasSubmitted_differentPair() = runTest {
        repo.submit(makeRating(userId = "u1", lessonId = "l1"))

        val result = repo.hasSubmitted("u1", LessonId("l2")).take(1).first()

        assertFalse(result, "hasSubmitted must be false for different lessonId")
    }

    @Test
    fun ratingAttemptProofIsFrozenInTheSameQueuedSubmissionAsTheLocalFlag() = runTest {
        val dao = FakeLessonRatingLocalDao()
        val repository = LessonRatingRepositoryImpl(dao, FakeContentHierarchy.roomWriter())
        val rating = makeRating(lessonId = FakeContentHierarchy.LESSON_ID)
            .copy(attemptId = AttemptId("completed-attempt"))

        assertTrue(repository.submit(rating).isSuccess)

        val queued = Json.parseToJsonElement(dao.outboxRows.single().payload).jsonObject
        assertEquals("completed-attempt", queued.getValue("attemptId").jsonPrimitive.content)
        assertEquals(rating.userId, queued.getValue("userId").jsonPrimitive.content)
        assertEquals(rating.lessonId.value, queued.getValue("lessonId").jsonPrimitive.content)
        assertTrue(repository.hasSubmitted(rating.userId, rating.lessonId).first())
    }

    @Test
    fun legacyRatingDoesNotInventAttemptProof() = runTest {
        val writer = FakeContentHierarchy.roomWriter()
        val rating = makeRating(lessonId = FakeContentHierarchy.LESSON_ID)
        val queued = Json.parseToJsonElement(writer.buildRatingRow(rating).payload).jsonObject
        assertFalse("attemptId" in queued)
    }
}
