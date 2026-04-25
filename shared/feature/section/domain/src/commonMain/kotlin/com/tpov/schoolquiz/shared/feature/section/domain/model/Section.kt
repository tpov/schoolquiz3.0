package com.tpov.schoolquiz.shared.feature.section.domain.model

import com.tpov.schoolquiz.shared.feature.quest.domain.model.QuestId

/**
 * Domain entity representing a section within a [Quest].
 *
 * A section groups themes and is the first level of content nesting inside
 * a quest. Sections are stored in the Firestore collection `sections/{sectionId}`
 * with a `questId` reference to their parent quest.
 *
 * [order] determines display sequence within the quest. Ordering display logic
 * is not in scope for this MVP — field is stored for future use.
 *
 * Spec: docs/features/home-and-my-quests/0-spec.md
 *   FR#12, Feature Domain Contract — Section invariants (scenario 18).
 *   [Codex fix #5] archived: consistent soft-delete across all hierarchy levels.
 */
data class Section(
    val id: SectionId,
    val questId: QuestId,
    val title: String,
    /**
     * Display order within the parent quest. Non-negative.
     * Invariant: >= 0.
     */
    val order: Int,
    /**
     * Monotonically-increasing version of this section's own fields.
     * Invariant: >= 1.
     */
    val version: Long,
    /**
     * Version counter tracking changes in child themes.
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
     * Soft-delete flag. When `true`, the client deletes the local section entry.
     * Consistent with Catalog/Quest archived semantics.
     * Default `false` for backward compatibility.
     */
    val archived: Boolean = false,
) {
    init {
        require(title.isNotBlank()) { "Section.title must not be blank" }
        require(order >= 0) { "Section.order must be >= 0, got $order" }
        require(version >= 1) { "Section.version must be >= 1, got $version" }
        require(contentsVersion >= 0) { "Section.contentsVersion must be >= 0, got $contentsVersion" }
        require(lastModifiedAt >= 0) { "Section.lastModifiedAt must be >= 0, got $lastModifiedAt" }
    }
}
