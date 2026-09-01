package com.tpov.schoolquiz.shared.feature.theme.domain.model

import com.tpov.schoolquiz.shared.feature.section.domain.model.SectionId

/**
 * Domain entity representing a theme within a [Section].
 *
 * A theme groups lessons and is the second level of content nesting. Stored
 * in Firestore collection `themes/{themeId}` with a [sectionId] reference.
 *
 * Spec: docs/features/home-and-my-quests/0-spec.md
 *   FR#12, Feature Domain Contract — Theme invariants (scenario 18).
 *   [Codex fix #5] archived: consistent soft-delete across all hierarchy levels.
 */
data class Theme(
    val id: ThemeId,
    val sectionId: SectionId,
    val title: String,
    /**
     * Display order within the parent section. Non-negative.
     * Invariant: >= 0.
     */
    val order: Int,
    /**
     * Monotonically-increasing version of this theme's own fields.
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
     * Soft-delete flag. When `true`, the client deletes the local theme entry.
     * Default `false` for backward compatibility.
     */
    val archived: Boolean = false,
) {
    init {
        require(title.isNotBlank()) { "Theme.title must not be blank" }
        require(order >= 0) { "Theme.order must be >= 0, got $order" }
        require(version >= 1) { "Theme.version must be >= 1, got $version" }
        require(lastModifiedAt >= 0) { "Theme.lastModifiedAt must be >= 0, got $lastModifiedAt" }
    }
}
