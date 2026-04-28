package com.tpov.schoolquiz.shared.feature.lesson_runner.domain

import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.RatingId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider.AttemptIdProvider
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.provider.RatingIdProvider
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Phase-01 compile-time check #13: AttemptIdProvider and RatingIdProvider interfaces exist
 * and are usable with the renamed AttemptId.value / RatingId.value (post-rename).
 *
 * These tests pass trivially if the interfaces exist — they are compile-time regression tests.
 */
class ProviderInterfaceTest {

    @Test
    fun `given AttemptIdProvider interface when instantiated as anonymous object then compilable`() {
        // Spec scenario #13a: attemptIdProvider_interface_exists
        val provider: AttemptIdProvider = object : AttemptIdProvider {
            override fun next(): AttemptId = AttemptId("test-attempt-id")
        }
        assertNotNull(provider.next())
    }

    @Test
    fun `given RatingIdProvider interface when instantiated as anonymous object then compilable`() {
        // Spec scenario #13b: ratingIdProvider_interface_exists
        val provider: RatingIdProvider = object : RatingIdProvider {
            override fun provide(userId: String, lessonId: LessonId): RatingId =
                RatingId("rating-$userId")
        }
        assertNotNull(provider.provide("user1", LessonId("l1")))
    }
}
