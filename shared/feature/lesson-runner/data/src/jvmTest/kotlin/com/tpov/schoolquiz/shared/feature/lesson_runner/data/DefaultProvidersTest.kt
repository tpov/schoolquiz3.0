package com.tpov.schoolquiz.shared.feature.lesson_runner.data

import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.provider.DefaultAttemptIdProvider
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.provider.DefaultRatingIdProvider
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.provider.DefaultRandomSeedProvider
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for DefaultAttemptIdProvider, DefaultRatingIdProvider, DefaultRandomSeedProvider.
 * Source: docs/features/lesson-runner/plan/phase-03/tests.md §Prov-01..Prov-05
 *
 * Providers live in androidMain (UUID, MessageDigest, currentTimeMillis are standard JVM APIs);
 * tests are in jvmTest to access the platform implementations directly.
 *
 * Run with: ./gradlew :shared:feature:lesson-runner:data:jvmTest --no-configuration-cache
 */
class DefaultProvidersTest {

    // Prov-01: GIVEN DefaultAttemptIdProvider WHEN next() THEN AttemptId.value.isNotBlank()
    @Test
    fun defaultAttemptIdProvider_returnsNonEmpty() {
        val result = DefaultAttemptIdProvider().next()

        assertTrue("AttemptId.value must not be blank", result.value.isNotBlank())
    }

    // Prov-02: GIVEN DefaultAttemptIdProvider WHEN next() twice THEN two different AttemptId values
    @Test
    fun defaultAttemptIdProvider_returnsUniqueIds() {
        val provider = DefaultAttemptIdProvider()
        val id1 = provider.next()
        val id2 = provider.next()

        assertNotEquals("Two consecutive next() calls must return different IDs", id1.value, id2.value)
    }

    // Prov-03: GIVEN DefaultRatingIdProvider WHEN provide("u1", LessonId("l1")) twice
    //          THEN both RatingId.value are equal (deterministic sha256)
    @Test
    fun defaultRatingIdProvider_deterministic() {
        val provider = DefaultRatingIdProvider()
        val id1 = provider.provide("u1", LessonId("l1"))
        val id2 = provider.provide("u1", LessonId("l1"))

        assertTrue("Same (userId, lessonId) must produce same RatingId", id1.value == id2.value)
    }

    // Prov-04: GIVEN DefaultRatingIdProvider WHEN provide("u1","l1") and provide("u1","l2")
    //          THEN different RatingId.value
    @Test
    fun defaultRatingIdProvider_differentInputs_differentIds() {
        val provider = DefaultRatingIdProvider()
        val id1 = provider.provide("u1", LessonId("l1"))
        val id2 = provider.provide("u1", LessonId("l2"))

        assertNotEquals("Different lessonId must produce different RatingId", id1.value, id2.value)
    }

    // Prov-05: GIVEN DefaultRandomSeedProvider WHEN next() THEN returns non-zero Long
    @Test
    fun defaultRandomSeedProvider_returnsLong() {
        val seed = DefaultRandomSeedProvider().next()

        assertTrue("Seed from System.currentTimeMillis() must be > 0", seed > 0L)
    }
}
