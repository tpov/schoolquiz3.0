package com.tpov.schoolquiz.shared.feature.lesson.domain

import com.tpov.schoolquiz.shared.core.leaderboard.TopParticipant
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.Lesson
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.theme.domain.model.ThemeId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Phase-01 tests: Group D — Lesson new rating/leaderboard fields.
 *
 * Requires backend-dev to:
 *  1. Create shared/core/leaderboard module with TopParticipant (ADR-LR-05).
 *  2. Add averageRating: Float?, ratingCount: Int = 0, top3: List<TopParticipant>
 *     to Lesson with defaults (ADR-LR-15).
 *  3. Add implementation(project(":shared:core:leaderboard")) to
 *     shared/feature/lesson/domain/build.gradle.kts (backend-dev scaffold).
 */
class LessonNewFieldsTest {

    // ── Group D: Lesson new fields ────────────────────────────────────────────

    @Test
    fun `given Lesson constructed with no rating data when access fields then defaults are returned`() {
        // Spec scenario D-01: lesson_defaultFields_noRatingData
        val lesson = makeLesson()

        assertNull(lesson.averageRating, "averageRating must default to null")
        assertEquals(0, lesson.ratingCount, "ratingCount must default to 0")
        assertTrue(lesson.top3.isEmpty(), "top3 must default to emptyList()")
    }

    @Test
    fun `given Lesson with top3 when access top3 then returns populated list`() {
        // Spec scenario D-02: lesson_withTop3_notEmpty
        val participant = TopParticipant(nickname = "Alice", avatarUrl = null, percent = 90)
        val lesson = makeLesson(top3 = listOf(participant))

        assertEquals(1, lesson.top3.size)
        assertEquals("Alice", lesson.top3.first().nickname)
        assertEquals(90, lesson.top3.first().percent)
    }

    @Test
    fun `given Lesson with averageRating and ratingCount when access fields then values match`() {
        // Spec scenario D-03: lesson_withAverageRating_nonNull
        val lesson = makeLesson(averageRating = 2.5f, ratingCount = 10)

        assertEquals(2.5f, lesson.averageRating)
        assertEquals(10, lesson.ratingCount)
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun makeLesson(
        id: String = "l1",
        themeId: String = "t1",
        title: String = "Lesson Title",
        order: Int = 0,
        version: Long = 1L,
        contentsVersion: Long = 0L,
        lastModifiedAt: Long = 0L,
        archived: Boolean = false,
        averageRating: Float? = null,
        ratingCount: Int = 0,
        top3: List<TopParticipant> = emptyList(),
    ) = Lesson(
        id = LessonId(id),
        themeId = ThemeId(themeId),
        title = title,
        order = order,
        version = version,
        contentsVersion = contentsVersion,
        lastModifiedAt = lastModifiedAt,
        archived = archived,
        averageRating = averageRating,
        ratingCount = ratingCount,
        top3 = top3,
    )
}
