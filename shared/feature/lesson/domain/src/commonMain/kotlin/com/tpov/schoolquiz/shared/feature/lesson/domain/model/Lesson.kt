package com.tpov.schoolquiz.shared.feature.lesson.domain.model

import com.tpov.schoolquiz.shared.core.leaderboard.TopParticipant
import com.tpov.schoolquiz.shared.feature.theme.domain.model.ThemeId

/**
 * Domain entity representing a lesson within a [Theme].
 *
 * A lesson is the direct parent of questions. Stored in Firestore collection
 * `lessons/{lessonId}` with a [themeId] reference.
 *
 * Spec: docs/features/home-and-my-quests/0-spec.md
 *   FR#12, Feature Domain Contract — Lesson invariants (scenario 18).
 *   [Codex fix #5] archived: consistent soft-delete across all hierarchy levels.
 */
data class Lesson(
    val id: LessonId,
    val themeId: ThemeId,
    val title: String,
    /**
     * Display order within the parent theme. Non-negative.
     * Invariant: >= 0.
     */
    val order: Int,
    /**
     * Monotonically-increasing version of this lesson's own fields.
     * Invariant: >= 1.
     */
    val version: Long,
    /**
     * Version counter tracking changes in child questions.
     * Invariant: >= 0.
     */
    val contentsVersion: Long,
    /**
     * Server-set timestamp (Unix millis) of the last write.
     * Used as delta-sync cursor: `where('lastModifiedAt', '>', localCursor)`.
     * Set by Firestore FieldValue.serverTimestamp() at write time.
     * Invariant: >= 0.
     */
    val lastModifiedAt: Long,
    /**
     * Soft-delete flag. When `true`, the client deletes the local lesson entry.
     * Default `false` for backward compatibility.
     */
    val archived: Boolean = false,
    val averageRating: Float? = null,
    val ratingCount: Int = 0,
    val top3: List<TopParticipant> = emptyList(),
) {
    init {
        require(title.isNotBlank()) { "Lesson.title must not be blank" }
        require(order >= 0) { "Lesson.order must be >= 0, got $order" }
        require(version >= 1) { "Lesson.version must be >= 1, got $version" }
        require(contentsVersion >= 0) { "Lesson.contentsVersion must be >= 0, got $contentsVersion" }
        require(lastModifiedAt >= 0) { "Lesson.lastModifiedAt must be >= 0, got $lastModifiedAt" }
        require(top3.size <= 3) { "Lesson.top3.size must be <= 3, got ${top3.size}" }
    }
}
