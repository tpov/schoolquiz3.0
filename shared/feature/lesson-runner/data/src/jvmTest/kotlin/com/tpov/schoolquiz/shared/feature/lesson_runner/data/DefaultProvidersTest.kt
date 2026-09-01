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

    /**
     * Идентификатор — у попытки оценить, а не у пары «игрок и урок».
     *
     * Постоянный идентификатор давал постоянный ключ идемпотентности, и оценка, чья запись ушла в
     * карантин, не могла быть поставлена заново: откат снимал локальную отметку, а ключ оставался
     * занят терминальной записью, и новая строка в очередь не вставала.
     */
    @Test
    fun defaultRatingIdProvider_givesEachAttemptItsOwnId() {
        val provider = DefaultRatingIdProvider()

        val id1 = provider.provide("u1", LessonId("l1"))
        val id2 = provider.provide("u1", LessonId("l1"))

        assertNotEquals("Каждая попытка оценить — своё намерение и свой ключ", id1.value, id2.value)
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

    /** Форма прежняя: 64 шестнадцатеричных знака, что бы ни было в прообразе. */
    @Test
    fun defaultRatingIdProvider_keepsTheShapeOfAHash() {
        val id = DefaultRatingIdProvider().provide("u1", LessonId("l1"))

        assertTrue("RatingId должен остаться шестнадцатеричной SHA-256", Regex("^[0-9a-f]{64}$").matches(id.value))
    }

    // Prov-05: GIVEN DefaultRandomSeedProvider WHEN next() THEN returns non-zero Long
    @Test
    fun defaultRandomSeedProvider_returnsLong() {
        val seed = DefaultRandomSeedProvider().next()

        assertTrue("Seed from System.currentTimeMillis() must be > 0", seed > 0L)
    }
}
