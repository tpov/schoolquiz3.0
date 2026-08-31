package com.tpov.schoolquiz.shared.feature.lesson_runner.domain

import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.Attempt
import com.tpov.schoolquiz.shared.core.scoring.CodeAnswer
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.LessonRating
import com.tpov.schoolquiz.shared.core.scoring.PercentScore
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.RatingId
import com.tpov.schoolquiz.shared.core.scoring.Stars
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Spec scenarios 62-71: Value object guard tests.
 * Each value class enforces its invariants via `require(...)` in init.
 */
class ValueObjectGuardTest {

    // ── Stars (tests 62-63) ───────────────────────────────────────────────────

    @Test
    fun `given Stars rawTenths 31 then throws IllegalArgumentException`() {
        // Spec scenario #62
        assertFailsWith<IllegalArgumentException>("Spec scenario #62: Stars(31) out of range [0..30]") {
            Stars(31)
        }
    }

    @Test
    fun `given Stars rawTenths -1 then throws`() {
        // Spec scenario #63
        assertFailsWith<IllegalArgumentException>("Spec scenario #63: Stars(-1) out of range") {
            Stars(-1)
        }
    }

    // ── PercentScore (test 64) ────────────────────────────────────────────────

    @Test
    fun `given PercentScore 101 then throws`() {
        // Spec scenario #64
        assertFailsWith<IllegalArgumentException>("Spec scenario #64: PercentScore(101) out of range [0..100]") {
            PercentScore(101)
        }
    }

    // ── LessonRating (tests 65-66) ────────────────────────────────────────────

    @Test
    fun `given LessonRating rating 4 then throws`() {
        // Spec scenario #65: range is 1..3
        assertFailsWith<IllegalArgumentException>("Spec scenario #65: rating=4 out of 1..3") {
            LessonRating(
                id = RatingId("r1"),
                userId = "user1",
                lessonId = LessonId("l1"),
                lessonVersion = 1L,
                rating = 4,
                ratedAt = 0L,
            )
        }
    }

    @Test
    fun `given LessonRating rating 0 then throws`() {
        // Spec scenario #66
        assertFailsWith<IllegalArgumentException>("Spec scenario #66: rating=0 out of 1..3") {
            LessonRating(
                id = RatingId("r1"),
                userId = "user1",
                lessonId = LessonId("l1"),
                lessonVersion = 1L,
                rating = 0,
                ratedAt = 0L,
            )
        }
    }

    // ── CodeAnswer (tests 67-68) ──────────────────────────────────────────────

    @Test
    fun `given CodeAnswer empty string then throws`() {
        // Spec scenario #67 (also in CodeAnswerTest)
        assertFailsWith<IllegalArgumentException>("Spec scenario #67: empty CodeAnswer") {
            CodeAnswer("")
        }
    }

    @Test
    fun `given CodeAnswer with non-digit char then throws`() {
        // Spec scenario #68 (also in CodeAnswerTest)
        assertFailsWith<IllegalArgumentException>("Spec scenario #68: 'X' is not in '0'..'9'") {
            CodeAnswer("12X45")
        }
    }

    // ── Attempt (tests 69-71) ─────────────────────────────────────────────────

    @Test
    fun `given Attempt with blank userId then throws`() {
        // Spec scenario #69
        assertFailsWith<IllegalArgumentException>("Spec scenario #69: blank userId") {
            Attempt(
                id = AttemptId("a1"),
                userId = "",
                lessonId = LessonId("l1"),
                lessonVersion = 1L,
                mode = Difficulty.EASY,
                completedAt = 0L,
                codeAnswer = CodeAnswer("9"),
                percentScore = PercentScore(100),
            )
        }
    }

    @Test
    fun `given Attempt with lessonVersion 0 then throws`() {
        // Spec scenario #70
        assertFailsWith<IllegalArgumentException>("Spec scenario #70: lessonVersion=0 must be >= 1") {
            Attempt(
                id = AttemptId("a1"),
                userId = "user1",
                lessonId = LessonId("l1"),
                lessonVersion = 0L,
                mode = Difficulty.EASY,
                completedAt = 0L,
                codeAnswer = CodeAnswer("9"),
                percentScore = PercentScore(100),
            )
        }
    }

    @Test
    fun `given Attempt with completedAt -1 then throws`() {
        // Spec scenario #71
        assertFailsWith<IllegalArgumentException>("Spec scenario #71: completedAt=-1 must be >= 0") {
            Attempt(
                id = AttemptId("a1"),
                userId = "user1",
                lessonId = LessonId("l1"),
                lessonVersion = 1L,
                mode = Difficulty.EASY,
                completedAt = -1L,
                codeAnswer = CodeAnswer("9"),
                percentScore = PercentScore(100),
            )
        }
    }

    // ── AttemptId rename regression (Group C) ─────────────────────────────────
    // Spec scenarios C-01, C-02: raw → value rename.
    // These tests verify the rename compiled correctly (.raw must no longer exist).

    @Test
    fun `given AttemptId when access value field then returns the wrapped string`() {
        // Spec scenario C-01: attemptId_value_field_accessible
        val id = AttemptId("abc-123")
        assertEquals("abc-123", id.value)
    }

    @Test
    fun `given RatingId when access value field then returns the wrapped string`() {
        // Spec scenario C-02: ratingId_value_field_accessible
        val id = RatingId("sha256hash")
        assertEquals("sha256hash", id.value)
    }
}
