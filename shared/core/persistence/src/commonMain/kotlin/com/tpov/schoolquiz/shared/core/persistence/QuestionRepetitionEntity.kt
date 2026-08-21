package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * Spaced-repetition state: one row per (user, question).
 *
 * Kept apart from `question_answers` on purpose — that table is an append-only history, this one is
 * mutable current state. Deriving the schedule from the whole history on every read would get
 * slower with every answer.
 *
 * Named "repetition", not "review": in this project *review* already means content moderation
 * (`review_assignments`), and reusing the word would make both harder to search for.
 *
 * The columns are the SM-2 working set, so the schedule can move from the simple starting rule to
 * the full algorithm without touching stored data.
 */
@Entity(
    tableName = "question_repetition",
    primaryKeys = ["user_id", "question_id"],
    indices = [
        Index(value = ["user_id", "next_review_at_ms"], name = "idx_question_repetition_due"),
    ],
)
data class QuestionRepetitionEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "question_id") val questionId: String,
    @ColumnInfo(name = "lesson_id") val lessonId: String,
    /** Current gap between showings, in days. */
    @ColumnInfo(name = "interval_days") val intervalDays: Int,
    /** SM-2 ease factor, scaled by 1000 to stay an integer (2500 == 2.5). */
    @ColumnInfo(name = "ease_factor_milli") val easeFactorMilli: Int,
    /** Consecutive successful recalls; reset to 0 on a lapse. */
    @ColumnInfo(name = "repetitions") val repetitions: Int,
    @ColumnInfo(name = "last_answered_at_ms") val lastAnsweredAtMs: Long,
    @ColumnInfo(name = "next_review_at_ms") val nextReviewAtMs: Long,
)
