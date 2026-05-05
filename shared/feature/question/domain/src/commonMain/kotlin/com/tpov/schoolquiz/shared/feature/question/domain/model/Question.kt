package com.tpov.schoolquiz.shared.feature.question.domain.model

import com.tpov.schoolquiz.shared.core.question_schema.QuestionLanguageLevel
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId

/**
 * Domain entity representing a question within a [Lesson].
 *
 * Question is the **leaf** of the 6-level hierarchy — it has no children and
 * therefore no [contentsVersion]. Stored in Firestore `questions/{questionId}`
 * with a [lessonId] reference.
 *
 * [payload] is a serialized JSON string conforming to ADR-0003 question schema
 * (SingleChoice, MultipleChoice, Ordering, FillBlank types). Domain layer treats
 * it as an opaque blob.
 *
 * [language] is an ISO 639-1 language code (e.g. "ru", "en"). The field is stored
 * but not used for filtering in MVP — it is ready for a future multilanguage feature.
 * [languageLevel] is the reviewer/translator language-level marker used by the review workflow.
 *
 * Spec: docs/features/home-and-my-quests/0-spec.md
 *   FR#13, Feature Domain Contract — Question invariants (scenarios 19-20).
 *   [Codex fix #5] archived: consistent soft-delete across all hierarchy levels.
 */
data class Question(
    val id: QuestionId,
    val lessonId: LessonId,
    val text: String,
    /**
     * Serialized JSON body of the question (answers, options, correct indices).
     * Must not be blank. Format: ADR-0003.
     * Invariant: non-blank.
     */
    val payload: String,
    /**
     * ISO 639-1 language code. Stored for future multilanguage support.
     * Invariant: non-blank.
     */
    val language: String,
    /**
     * Non-negative language review level. Defaults to 1 for existing content and early authoring flows.
     */
    val languageLevel: Int = QuestionLanguageLevel.DEFAULT,
    /**
     * Display order within the parent lesson. Non-negative.
     * Invariant: >= 0.
     */
    val order: Int,
    /**
     * Monotonically-increasing version of this question's own fields.
     * Leaf node — no contentsVersion.
     * Invariant: >= 1.
     */
    val version: Long,
    /**
     * Server-set timestamp (Unix millis) of the last write.
     * Used as delta-sync cursor: `where('lastModifiedAt', '>', localCursor)`.
     * Set by Firestore FieldValue.serverTimestamp() at write time.
     * Invariant: >= 0.
     */
    val lastModifiedAt: Long,
    /**
     * Soft-delete flag. When `true`, the client deletes the local question entry.
     * Default `false` for backward compatibility.
     */
    val archived: Boolean = false,
) {
    init {
        require(text.isNotBlank()) { "Question.text must not be blank" }
        require(payload.isNotBlank()) { "Question.payload must not be blank" }
        require(language.isNotBlank()) { "Question.language must not be blank" }
        QuestionLanguageLevel.requireValid(languageLevel, "Question.languageLevel")
        require(order >= 0) { "Question.order must be >= 0, got $order" }
        require(version >= 1) { "Question.version must be >= 1, got $version" }
        require(lastModifiedAt >= 0) { "Question.lastModifiedAt must be >= 0, got $lastModifiedAt" }
    }
}
