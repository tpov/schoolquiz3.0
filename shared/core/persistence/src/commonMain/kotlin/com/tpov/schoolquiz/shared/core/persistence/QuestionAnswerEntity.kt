package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * Append-only log: one row per answered question.
 *
 * `lesson_attempts` keeps a single digit per question, which scores the lesson but answers nothing
 * else. This table is the source for spaced repetition, per-lesson statistics, the option
 * distribution a survey needs, and the timing signals that expose automated play.
 *
 * Deliberately not tied to `questions` by a foreign key: the log has to outlive content edits, and
 * a cascade from a re-published question would erase the very history these features rely on.
 */
@Entity(
    tableName = "question_answers",
    primaryKeys = ["attempt_id", "question_id"],
    indices = [
        Index(value = ["user_id", "question_id"], name = "idx_question_answers_user_question"),
        Index(value = ["lesson_id"], name = "idx_question_answers_lesson"),
        Index(value = ["answered_at_ms"], name = "idx_question_answers_answered_at"),
    ],
)
data class QuestionAnswerEntity(
    @ColumnInfo(name = "attempt_id") val attemptId: String,
    @ColumnInfo(name = "question_id") val questionId: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "lesson_id") val lessonId: String,
    @ColumnInfo(name = "lesson_version") val lessonVersion: Long,
    @ColumnInfo(name = "is_hard") val isHard: Int,
    /** Position of this question inside the attempt's codeAnswer string. */
    @ColumnInfo(name = "code_answer_index") val codeAnswerIndex: Int,
    /** The 1..9 digit written into that position. */
    @ColumnInfo(name = "score") val score: Int,
    /** The submitted answer, serialized — which option was actually chosen. */
    @ColumnInfo(name = "answer_payload") val answerPayload: String,
    @ColumnInfo(name = "answered_at_ms") val answeredAtMs: Long,
    /** Time spent on the question; 0 when the session was restored and the start is unknown. */
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    /** The timer answered instead of the player. */
    @ColumnInfo(name = "was_timeout") val wasTimeout: Int,
)
